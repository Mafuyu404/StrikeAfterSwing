#!/usr/bin/env python3
"""Behaviour test for StrikeAfterSwing.

The mod defers ``Mob#doHurtTarget`` by the attacker's current swing duration. This
test measures that deferral on a live dedicated server instead of only checking
that the mixins were applied.

Two identical arenas are set up far apart: each holds one immobile villager and one
zombie standing next to it, so the AI attacks as soon as it acquires the target.

* baseline arena: the zombie's swing duration is the vanilla 6 ticks.
* long-swing arena: the zombie gets Mining Fatigue at amplifier 255, which pushes
  ``LivingEntity#getCurrentSwingDuration`` to ``6 + (1 + 255) * 2 = 518`` ticks.

The first hit on each villager is timestamped with ``time query gametime``, so the
measurement is in game ticks and therefore independent of server lag. With the mod
loaded the long-swing zombie lands its first hit hundreds of ticks after the
baseline one; without the deferral both land at the same time. A queue that never
ticks (broken server-tick injection) shows up as "no damage at all".

Everything that is version specific lives in commands.json and an optional
``targets/<target>/behavior-test.json`` override, so the assertions below stay
identical for every target.
"""

import argparse
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import rcon  # noqa: E402  (module next to this file)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))

PAD_Y = 100          # platform the test mobs stand on
ENTITY_Y = 101       # mob feet, one block above the platform
PAD_RADIUS = 2       # 5x5 platform
ATTACKER_OFFSET = 1  # zombie block next to the villager, well inside attack reach

BASELINE_SITE = {'name': 'baseline', 'x': 0, 'z': 0, 'longSwing': False}
LONG_SWING_SITE = {'name': 'long-swing', 'x': 100, 'z': 0, 'longSwing': True}
SITES = [BASELINE_SITE, LONG_SWING_SITE]

DEFAULT_ASSERTIONS = {
    'pollTimeoutSeconds': 300,
    'visibilityTimeoutSeconds': 90,
    'windowTicks': 1000,
    'maxBaselineFirstHitTick': 200,
    'minDeferralTicks': 300,
    'maxLongSwingHitsInWindow': 2,
    'minBaselineHits': 2,
}


class TestFailure(Exception):
    pass


def load_settings(target):
    with open(os.path.join(SCRIPT_DIR, 'commands.json'), 'r', encoding='utf-8') as handle:
        settings = json.load(handle)

    overrides = []
    if target:
        override_path = os.path.join(REPO_ROOT, 'targets', target, 'behavior-test.json')
        if os.path.isfile(override_path):
            with open(override_path, 'r', encoding='utf-8') as handle:
                overrides.append(('targets/%s/behavior-test.json' % target, json.load(handle)))

    for source, override in overrides:
        for key, value in override.items():
            if key.startswith('_'):
                continue
            if isinstance(value, dict) and isinstance(settings.get(key), dict):
                settings[key].update(value)
            else:
                settings[key] = value
        print('    merged override: %s' % source)

    return settings


def context_for(site):
    x = site['x']
    z = site['z']
    return {
        'x': x,
        'y': ENTITY_Y,
        'z': z,
        'padY': PAD_Y,
        'padX1': x - PAD_RADIUS,
        'padX2': x + PAD_RADIUS,
        'padZ1': z - PAD_RADIUS,
        'padZ2': z + PAD_RADIUS,
        'attackerX': x + ATTACKER_OFFSET,
        'targetTag': 'sas_target_%s' % site['name'],
        'attackerTag': 'sas_attacker_%s' % site['name'],
    }


def expand(template, context):
    return template.format(**context)


def read_tick(connection, template):
    return int(rcon.parse_number(connection.command(template)))


def read_health(connection, template, tag):
    """Returns the entity health, or None when the entity is gone."""
    response = connection.command(template.format(tag=tag))
    if 'no entity was found' in response.lower():
        return None
    return rcon.parse_number(response)


def wait_until_selectable(connection, sites, assertions):
    """Waits until every spawned mob can be found by a tag selector.

    A freshly summoned entity only becomes visible to selectors once its chunk status
    has settled, which can lag the summon by about a second on a server that just
    started. Measuring before that makes the first commands fail with
    "No entity was found" even though the mobs exist (and do attack a moment later).
    """
    pending = []
    for state in sites:
        pending.append(state['context']['targetTag'])
        pending.append(state['context']['attackerTag'])

    deadline = time.time() + assertions['visibilityTimeoutSeconds']
    while pending and time.time() < deadline:
        still_hidden = []
        for tag in pending:
            response = connection.command('execute if entity @e[tag=%s]' % tag)
            if 'passed' not in response.lower():
                still_hidden.append(tag)
        pending = still_hidden
        if pending:
            time.sleep(0.1)

    if pending:
        raise TestFailure('entities never became selectable: %s' % ', '.join(pending))


def measure(connection, settings, assertions):
    """Sets up both arenas, then polls until every site recorded a first hit."""
    for command in settings['worldSetup']:
        require_command(connection, command)

    sites = []
    for site in SITES:
        context = context_for(site)
        state = {
            'site': site,
            'context': context,
            'firstHitTick': None,
            'hits': 0,
            'lastHealth': None,
            'targetGone': False,
        }
        sites.append(state)

        for command in settings['siteSetup']:
            require_command(connection, expand(command, context))
        require_command(connection, expand(settings['spawnTarget'], context))
        require_command(connection, expand(settings['spawnAttacker'], context))

    wait_until_selectable(connection, sites, assertions)

    start_tick = read_tick(connection, settings['queryTick'])

    for state in sites:
        if state['site']['longSwing']:
            require_command(connection, expand(settings['longSwing'], state['context']),
                            allow_empty=False)

    deadline = time.time() + assertions['pollTimeoutSeconds']
    while time.time() < deadline:
        tick = read_tick(connection, settings['queryTick']) - start_tick
        for state in sites:
            if state['targetGone']:
                continue
            health = read_health(connection, settings['queryHealth'], state['context']['targetTag'])
            if health is None:
                state['targetGone'] = True
                continue
            if state['lastHealth'] is None:
                state['lastHealth'] = health
            elif health < state['lastHealth']:
                state['hits'] += 1
                if state['firstHitTick'] is None:
                    state['firstHitTick'] = tick
                state['lastHealth'] = health

        if tick >= assertions['windowTicks']:
            break
        if all(state['firstHitTick'] is not None for state in sites):
            break
        time.sleep(0.02)

    return sites, start_tick


def require_command(connection, command, allow_empty=True):
    response = connection.command(command)
    rcon.require_ok(response, command, allow_empty=allow_empty)
    return response


def evaluate(sites, assertions):
    baseline = sites[0]
    long_swing = sites[1]

    if baseline['firstHitTick'] is None:
        raise TestFailure('baseline arena never took damage: the queued attack never '
                          'executed (broken server-tick injection?)')
    if long_swing['firstHitTick'] is None:
        raise TestFailure('long-swing arena never took damage within %d ticks: the '
                          'deferred attack never executed' % assertions['windowTicks'])

    deferral = long_swing['firstHitTick'] - baseline['firstHitTick']

    if baseline['firstHitTick'] > assertions['maxBaselineFirstHitTick']:
        raise TestFailure('baseline first hit at tick %d, above the %d tick bound: the '
                          'delay is not the expected small swing duration'
                          % (baseline['firstHitTick'], assertions['maxBaselineFirstHitTick']))
    if deferral < assertions['minDeferralTicks']:
        raise TestFailure('deferral is only %d ticks (baseline %d, long-swing %d); the mod '
                          'is not deferring by the attacker swing duration'
                          % (deferral, baseline['firstHitTick'], long_swing['firstHitTick']))
    if long_swing['hits'] > assertions['maxLongSwingHitsInWindow']:
        raise TestFailure('long-swing arena took %d hits in the window, expected at most %d: '
                          'attacks are stacking instead of staying queued'
                          % (long_swing['hits'], assertions['maxLongSwingHitsInWindow']))
    if baseline['hits'] < assertions['minBaselineHits']:
        raise TestFailure('baseline arena only took %d hits, expected at least %d'
                          % (baseline['hits'], assertions['minBaselineHits']))

    return deferral


def report(sites, deferral, assertions):
    print('')
    print('    measured (game ticks since the arenas were armed):')
    print('      %-12s %-12s %-8s %s' % ('arena', 'first hit', 'hits', 'final health'))
    for state in sites:
        print('      %-12s %-12s %-8d %s' % (
            state['site']['name'],
            state['firstHitTick'],
            state['hits'],
            'target removed' if state['targetGone'] else state['lastHealth'],
        ))
    print('')
    print('    deferral between the two arenas : %d ticks (min required %d)'
          % (deferral, assertions['minDeferralTicks']))
    print('    Mining Fatigue 255 makes the attacker swing duration 518 ticks, so the')
    print('    long-swing arena is expected to land its first hit ~512 ticks later.')


def main():
    parser = argparse.ArgumentParser(description='StrikeAfterSwing behaviour test')
    parser.add_argument('--target', required=True,
                        help='target directory name, e.g. forge-1.20.1')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--password', default='behaviortest')
    parser.add_argument('--port', type=int, default=None)
    parser.add_argument('--keep-running', action='store_true',
                        help='do not send "stop" to the server when finished')
    args = parser.parse_args()

    settings = load_settings(args.target)
    assertions = dict(DEFAULT_ASSERTIONS)
    assertions.update(settings.get('assertions', {}))
    port = args.port or settings.get('rconPort', 25575)

    print('== behaviour test: %s' % args.target)
    print('    rcon port %d, window %d ticks, poll timeout %ds'
          % (port, assertions['windowTicks'], assertions['pollTimeoutSeconds']))

    connection = None
    failures = []
    try:
        if not rcon.wait_for_port(args.host, port, assertions['pollTimeoutSeconds']):
            raise TestFailure('RCON port %d did not open' % port)

        connection = rcon.Rcon(args.host, port, args.password)
        sites, _ = measure(connection, settings, assertions)
        deferral = evaluate(sites, assertions)
        report(sites, deferral, assertions)
        print('')
        print('    RESULT: PASS')
    except (TestFailure, rcon.RconError) as error:
        failures.append(str(error))
        print('')
        print('    RESULT: FAIL - %s' % error)
    finally:
        if connection is not None:
            if not args.keep_running:
                try:
                    connection.command('stop')
                    print('    server stop requested')
                except Exception:
                    pass
            connection.close()
        else:
            print('    (no RCON connection, server left running for inspection)')

    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
