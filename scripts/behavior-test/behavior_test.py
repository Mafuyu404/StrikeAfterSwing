#!/usr/bin/env python3
"""Behaviour test for StrikeAfterSwing.

Every behaviour the mod implements gets its own scenario on a live dedicated server:

1. ``deferral`` - the core mechanic. A mob's hit is deferred by its current swing duration:
   a baseline husk (vanilla 6 tick swing) against a husk at Mining Fatigue 255 (518 tick
   swing). The deferral must match the swing duration, and the queue must stay usable. The
   scenarios also pin down the vanilla-override gate: the cancelled call must not report
   success, otherwise ``Husk#doHurtTarget`` fires its hunger debuff when the swing starts.
   Also checks that an attack is only ever applied once per swing (no stacking).
2. ``multiplier`` - the ``windup.json`` multiplier. With ``minecraft:husk`` set to x20 the
   husk's hit must land about twenty swing durations in, while a zombie with no entry keeps
   vanilla timing. The file is rewritten in place while the server runs, so this scenario
   also proves the config is picked up without a restart.
3. ``broken-config`` - a truncated JSON body must keep the last good multipliers instead of
   silently reverting to vanilla.
4. ``deleted-config`` - deleting the file must fall back to vanilla timing.
5. ``reach`` - the deferred hit re-checks melee reach. Two long-swing husks, one whose villager
   is teleported out of reach during the windup (must stay unharmed) and one whose villager
   stays put (must be hit, proving the husk really did swing).

The first hit on each villager is timestamped with ``time query gametime``, so every number is
in game ticks and therefore independent of server lag. With the mod loaded the long-swing
attacker lands its first hit hundreds of ticks after the baseline one; without the deferral
they land together, and a queue that never ticks shows up as "no damage at all".

Everything version specific lives in commands.json plus an optional
``targets/<target>/behavior-test.json`` override, so the scenarios and assertions below are
identical for every target.
"""

import argparse
import json
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import rcon  # noqa: E402  (module next to this file)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))

PAD_Y = 100          # platform the test mobs stand on
ENTITY_Y = 101       # mob feet, one block above the platform
PAD_RADIUS = 2       # 5x5 platform
ATTACKER_OFFSET = 1  # attacker block next to the villager, well inside attack reach

# The deferred hit is resolved against the attacker's swing duration, which Mining Fatigue 255
# pushes from 6 to 6 + (1 + 255) * 2 = 518 ticks.
VANILLA_SWING = 6
FATIGUE_SWING = 518

DELETED = '<deleted>'
BROKEN = '{\n  "minecraft:husk": 20.0\n'          # truncated on purpose

MULTIPLIER = 20.0
MULTIPLIER_SITE = 'scaled'
VANILLA_SITE = 'vanilla'
KEPT_SITE = 'kept'
FALLBACK_SITE = 'fallback'

# Arena A stays inside the spawn chunks; arena B needs the chunk of its own position loaded,
# which siteSetup does with proper chunk coordinates.
SCENARIOS = [
    {
        'name': 'deferral',
        'config': DELETED,
        'windowTicks': 1000,
        'sites': [
            {'name': 'baseline', 'x': 0, 'longSwing': False},
            {'name': 'long-swing', 'x': 100, 'longSwing': True},
        ],
    },
    {
        'name': 'multiplier',
        'config': {'minecraft:husk': MULTIPLIER},
        'windowTicks': 500,
        'sites': [
            {'name': MULTIPLIER_SITE, 'x': 0, 'attacker': 'husk'},
            {'name': VANILLA_SITE, 'x': 100, 'attacker': 'zombie', 'hunger': False},
        ],
    },
    {
        'name': 'broken-config',
        'config': BROKEN,
        'windowTicks': 500,
        'sites': [
            {'name': KEPT_SITE, 'x': 0, 'attacker': 'husk'},
        ],
    },
    {
        'name': 'deleted-config',
        'config': DELETED,
        'windowTicks': 500,
        'sites': [
            {'name': FALLBACK_SITE, 'x': 0, 'attacker': 'husk'},
        ],
    },
    {
        'name': 'reach',
        'config': DELETED,
        # The control husk lands its first hit at 518 plus its targeting phase, which has been
        # measured as high as ~100 ticks under load; keep the window well clear of that so the
        # control arena cannot miss it. The escaped villager is out of reach for the whole
        # window either way: its attacker moves at a tenth of its speed (Slowness 5) and covers
        # about 23 of the 29 blocks between them even over 1000 ticks.
        'windowTicks': 1000,
        'sites': [
            {'name': 'reach-control', 'x': 0, 'longSwing': True},
            {'name': 'reach-escape', 'x': 100, 'longSwing': True, 'slow': True,
             'padXRadius': 32, 'escapeTo': 130, 'settleAfter': 'reach-control'},
        ],
    },
]

DEFAULT_ASSERTIONS = {
    'pollTimeoutSeconds': 300,
    'visibilityTimeoutSeconds': 90,
    'tickRateWaitSeconds': 300,
    # A forceloaded chunk is only actually loaded on a later tick, so arena commands are retried
    # while the server still answers "That position is not loaded" (40 x 0.25s of slack).
    'chunkLoadRetrySeconds': 0.25,
    # The mod re-checks the config file every two seconds, and only while a mob is attacking.
    # Wait for two full intervals plus a margin so the next scenario's first swing cannot be
    # queued before the rewrite has been noticed.
    'reloadWaitSeconds': 5.0,
    'windowTicks': 1000,
    'maxBaselineFirstHitTick': 200,
    'minDeferralTicks': 300,
    'maxLongSwingHitsInWindow': 2,
    'minBaselineHits': 2,
    # How far the first hunger sample may precede the first damage sample on the long-swing
    # arena. Its deferral is 518 ticks, so an early debuff lands hundreds of ticks ahead of
    # the hit while correct behaviour keeps them within one poll of each other.
    'hungerToleranceTicks': 120,
    # multiplier scenario: x20 on a 6 tick swing is 120 ticks, the untouched zombie stays near
    # 6 + targeting phase. The thresholds sit far outside the observed spread of the latter
    # (22..62 ticks across earlier runs) so that neither bound can be reached by noise.
    'minScaledFirstHitTick': 100,
    'maxVanillaFirstHitTick': 90,
    'minTypeSeparationTicks': 40,
    'escapeAtTick': 200,
    # How long the escape arena keeps being watched after its paired control arena took the hit
    # that the escape arena would have taken at the same moment. The two attackers swing in
    # parallel a few ticks apart, so a small grace period is enough to show the hit did not
    # land; this is what keeps the scenario from sitting out the whole window.
    'escapeGraceTicks': 60,
    'minReachControlHits': 1,
}


class TestFailure(Exception):
    pass


def load_settings(target):
    with open(os.path.join(SCRIPT_DIR, 'commands.json'), 'r', encoding='utf-8') as handle:
        settings = json.load(handle)

    if target:
        override_path = os.path.join(REPO_ROOT, 'targets', target, 'behavior-test.json')
        if os.path.isfile(override_path):
            with open(override_path, 'r', encoding='utf-8') as handle:
                override = json.load(handle)
            for key, value in override.items():
                if key.startswith('_'):
                    continue
                if isinstance(value, dict) and isinstance(settings.get(key), dict):
                    settings[key].update(value)
                else:
                    settings[key] = value
            print('    merged override: targets/%s/behavior-test.json' % target)

    return settings


# Entity tags carry a per-run suffix: a villager killed in an earlier run keeps its tag
# through the death animation, and a selector matching that leftover would mix up the
# health, hunger and effect samples of two different entities.
RUN_TAG = os.urandom(3).hex()


def context_for(site, scenario):
    x = site['x']
    z = site.get('z', 0)
    pad_x = site.get('padXRadius', PAD_RADIUS)
    pad_z = site.get('padZRadius', PAD_RADIUS)
    name = '%s_%s' % (scenario['name'], site['name'])
    return {
        'x': x,
        'y': ENTITY_Y,
        'z': z,
        'padY': PAD_Y,
        'padX1': x - pad_x,
        'padX2': x + pad_x,
        'padZ1': z - pad_z,
        'padZ2': z + pad_z,
        'attackerX': x + ATTACKER_OFFSET,
        'escapeX': site.get('escapeTo', x),
        'attacker': site.get('attacker', 'husk'),
        'targetTag': 'sas_target_%s_%s' % (name, RUN_TAG),
        'attackerTag': 'sas_attacker_%s_%s' % (name, RUN_TAG),
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


EFFECT_ID_PATTERN = re.compile(r'(?i)\bid\s*:\s*"?([a-z_:]+|\d+)"?')


def read_hunger(connection, template, tag):
    """True when the entity currently carries the hunger effect.

    Effects are stored as ``{Id: 17, ...}`` up to 1.20.4 and as
    ``{id: "minecraft:hunger", ...}`` from 1.20.5 on, so both forms are accepted.
    """
    response = connection.command(template.format(tag=tag))
    for value in EFFECT_ID_PATTERN.findall(response):
        if value.isdigit():
            if int(value) == 17:
                return True
        elif value == 'minecraft:hunger':
            return True
    return False


def require_command(connection, command, allow_empty=True):
    response = connection.command(command)
    rcon.require_ok(response, command, allow_empty=allow_empty)
    return response


def require_loaded_command(connection, command, assertions, attempts=40):
    """Runs a command, retrying while the server reports the position as unloaded.

    ``forceload`` only marks chunks; the server actually loads them on a later tick. On a world
    that has never generated those chunks (CI checks out a clean tree and the driver creates the
    run directory from scratch) the fill that follows immediately can therefore fail with "That
    position is not loaded", and anything summoned into that chunk would never tick.

    Retrying is safe for everything run through here: the arena commands (forceload, fill, kill)
    are idempotent, and a summon that failed because the chunk was missing did not create
    anything to duplicate.
    """
    delay = assertions['chunkLoadRetrySeconds']
    for attempt in range(attempts):
        response = connection.command(command)
        if 'not loaded' not in response.lower():
            rcon.require_ok(response, command)
            return response
        time.sleep(delay)
    raise TestFailure('the chunk needed by %r never loaded (%d attempts, %.1fs apart)'
                      % (command, attempts, delay))


def wait_until_selectable(connection, tags, assertions):
    """Waits until every given tag can be found by a selector.

    A freshly summoned entity only becomes visible to selectors once its chunk status
    has settled, which can lag the summon by about a second on a server that just
    started. Acting before that makes commands fail with "No entity was found" even
    though the mobs exist (and do attack a moment later).
    """
    pending = list(tags)
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


def wait_for_tick_rate(connection, settings, assertions):
    """Waits until the server actually ticks.

    A starved server (heavy parallel builds on a developer machine, or a cold CI runner
    still settling after world generation) counts ticks correctly but runs everything so
    slowly that the arenas drift apart. Wait for it to settle instead of failing straight
    away; only refuse to report numbers when it never does. Every assertion is measured in
    game ticks, so a slow but stable server is fine - only a server that barely ticks at all
    would make the sampling meaningless.
    """
    deadline = time.time() + assertions['tickRateWaitSeconds']
    ticks_in_three_seconds = 0
    while time.time() < deadline:
        tick_before = read_tick(connection, settings['queryTick'])
        time.sleep(3)
        ticks_in_three_seconds = read_tick(connection, settings['queryTick']) - tick_before
        if ticks_in_three_seconds >= 9:
            return
    raise TestFailure(
        'server never reached a usable tick rate: only %d ticks in 3s (need 9) after '
        'waiting %ds' % (ticks_in_three_seconds, assertions['tickRateWaitSeconds']))


def config_path(target):
    return os.path.join(REPO_ROOT, 'targets', target, 'run', 'config', 'strikeafterswing',
                        'windup.json')


def apply_config(path, config):
    """Writes (or deletes) the windup config the way a server admin would."""
    if config is DELETED:
        if os.path.isfile(path):
            os.remove(path)
        return 'removed'
    if os.path.exists(path):
        os.remove(path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    body = config if isinstance(config, str) else json.dumps(config, indent=2) + '\n'
    with open(path, 'w', encoding='utf-8') as handle:
        handle.write(body)
    return 'wrote %r' % body.replace('\n', ' ')


def build_sites(connection, settings, assertions, scenario, site_defs):
    """Builds every arena of a scenario and spawns its attackers."""
    states = []
    for site in site_defs:
        context = context_for(site, scenario)
        states.append({
            'site': site,
            'context': context,
            'firstHitTick': None,
            'firstHungerTick': None,
            'hits': 0,
            'lastHealth': None,
            'targetGone': False,
            'escapedAt': None,
            'observedTicks': 0,
            'windowTicks': scenario.get('windowTicks', assertions['windowTicks']),
        })

        for command in settings['siteSetup']:
            require_loaded_command(connection, expand(command, context), assertions)

        require_loaded_command(connection, expand(settings['spawnAttacker'], context), assertions)

    # Wait until the attackers are selectable before touching them, then arm them. No villager
    # exists yet and an attacker without a target cannot attack, so nothing can be queued with
    # the vanilla swing duration. Never arm through "data merge": that round-trips the entity
    # NBT and the effect level is stored as a signed byte, so 255 comes back as 0.
    wait_until_selectable(connection,
                          [state['context']['attackerTag'] for state in states],
                          assertions)

    for state in states:
        if state['site'].get('longSwing'):
            require_command(connection, expand(settings['longSwing'], state['context']),
                            allow_empty=False)
            # Fail loudly when the arming did not take: an attacker without the effect lands
            # its hit together with the baseline one, which is easy to mistake for a mod
            # problem. Effect levels are stored as signed bytes, so the maximum reads as -1.
            response = connection.command(
                expand(settings['queryEffects'], state['context']))
            levels = [int(level) & 0xFF for level in
                      re.findall(r'(?i)amplifier:?\s*(-?\d+)', response)]
            if not levels or max(levels) < 200:
                raise TestFailure(
                    '%s: long-swing attacker carries no maximum-strength effect (levels: %s)'
                    % (state['site']['name'], levels or 'none'))
        if state['site'].get('slow'):
            require_command(connection, expand(settings['slowAttacker'], state['context']),
                            allow_empty=False)

    return states


def spawn_targets(connection, settings, assertions, states):
    for state in states:
        require_loaded_command(connection, expand(settings['spawnTarget'], state['context']),
                               assertions)
    wait_until_selectable(connection, [state['context']['targetTag'] for state in states],
                          assertions)


def escape_target(connection, settings, assertions, state):
    """Moves a villager out of the attacker's reach while its hit is still queued."""
    response = require_command(connection,
                               expand(settings['escapeTarget'], state['context']),
                               allow_empty=False)
    state['escapedAt'] = True
    return response


def sample(connection, settings, assertions, states, window_ticks, start_tick):
    """Polls every arena until the window closes or every arena has what it needs."""
    def satisfied(state, tick):
        """An arena is done once it recorded what its scenario wants to see.

        Arenas whose attacker does not apply the hunger debuff (a zombie, unlike a husk, does
        not) stop as soon as their hit is recorded. An arena that must *not* be hit is settled
        against the arena it is paired with: once that reference arena has taken the hit it
        should have taken, the escape arena only needs a grace period to show that the same
        hit did not land on it (the two attackers swing in parallel, a few ticks apart), so
        the run does not have to sit out the whole window.
        """
        if state['firstHitTick'] is not None:
            if state['targetGone']:
                return True
            if not state['site'].get('hunger', True):
                return True
            return state['firstHungerTick'] is not None
        reference_name = state['site'].get('settleAfter')
        if reference_name is None:
            return False
        reference = by_name(states, reference_name)
        if reference['firstHitTick'] is None:
            return False
        return tick - reference['firstHitTick'] >= assertions['escapeGraceTicks']

    deadline = time.time() + assertions['pollTimeoutSeconds']
    while time.time() < deadline:
        tick = read_tick(connection, settings['queryTick']) - start_tick
        for state in states:
            state['observedTicks'] = tick
            if state['targetGone']:
                continue
            if (state['site'].get('escapeTo') is not None
                    and state['escapedAt'] is None
                    and tick >= assertions['escapeAtTick']):
                escape_target(connection, settings, assertions, state)

            # Hunger is read first: a hit applies its damage and then its debuff in the same
            # tick, so observing the debuff already implies that hit's damage was applied. Both
            # values are stamped with this iteration's tick, which keeps them within one poll
            # of each other no matter where between the two commands the hit landed.
            has_hunger = read_hunger(connection, settings['queryTargetEffects'],
                                     state['context']['targetTag'])
            health = read_health(connection, settings['queryHealth'],
                                 state['context']['targetTag'])
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
            if has_hunger and state['firstHungerTick'] is None:
                state['firstHungerTick'] = tick

        if tick >= window_ticks:
            break
        # Keep polling until every arena has seen both a hit and the debuff that comes with it:
        # the hunger read runs before the health read, so a hit landing between those two
        # commands would otherwise end the loop without the next poll seeing its debuff.
        waiting = [state for state in states if not satisfied(state, tick)]
        if not waiting:
            break
        time.sleep(0.02)


def run_scenario(connection, settings, assertions, scenario, path):
    print('')
    print('    scenario %-14s config: %s' % (scenario['name'],
                                             apply_config(path, scenario['config'])))
    # The mod re-reads the file on a timer and only when a mob is actually attacking, so wait
    # in every scenario, deletions included: the next scenario's first swing has to happen more
    # than one check interval after the rewrite, or it would still be queued with the old
    # multiplier (which is exactly what a stale-config test must not measure).
    time.sleep(assertions['reloadWaitSeconds'])

    # Start from a clean world: leftover mobs from an earlier scenario would fight on their own
    # and burn ticks, and a leftover villager could still be selected by a stale tag.
    require_command(connection, 'kill @e[type=!player]')

    states = build_sites(connection, settings, assertions, scenario, scenario['sites'])
    start_tick = read_tick(connection, settings['queryTick'])
    spawn_targets(connection, settings, assertions, states)
    sample(connection, settings, assertions, states,
           scenario.get('windowTicks', assertions['windowTicks']), start_tick)
    report_scenario(scenario, states)
    return states


def report_scenario(scenario, states):
    print('      %-16s %-10s %-10s %-6s %s'
          % ('arena', 'first hit', 'hunger', 'hits', 'final health'))
    for state in states:
        escape = ''
        if state['site'].get('escapeTo') is not None:
            escape = '  (villager escaped)' if state['escapedAt'] else '  (escape never ran!)'
        print('      %-16s %-10s %-10s %-6d %s%s' % (
            state['site']['name'],
            state['firstHitTick'],
            state['firstHungerTick'],
            state['hits'],
            'target removed' if state['targetGone'] else state['lastHealth'],
            escape))


def by_name(states, name):
    for state in states:
        if state['site']['name'] == name:
            return state
    raise TestFailure('internal error: arena %s not found' % name)


def check_deferral(states, assertions):
    baseline = by_name(states, 'baseline')
    long_swing = by_name(states, 'long-swing')

    if baseline['firstHitTick'] is None:
        raise TestFailure('baseline arena never took damage: the queued attack never '
                          'executed (broken server-tick injection?)')
    if long_swing['firstHitTick'] is None:
        raise TestFailure('long-swing arena never took damage within %d ticks: the '
                          'deferred attack never executed'
                          % long_swing['windowTicks'])

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

    for state in states:
        name = state['site']['name']
        if state['firstHungerTick'] is None:
            raise TestFailure('%s arena: the hunger debuff never showed up, so the vanilla '
                              'doHurtTarget override did not run with the deferred hit' % name)
        # Only the long-swing arena can tell an early debuff apart from a sampling artefact:
        # its 518 tick swing puts a debuff fired by the cancelled call hundreds of ticks ahead
        # of the hit, while correct behaviour keeps the two inside one poll of each other. The
        # baseline arena swings for 6 ticks, far below the sampling resolution, so for it only
        # "the debuff happened at all" is checked.
        if state['site'].get('longSwing') and state['firstHitTick'] is not None:
            lead = state['firstHitTick'] - state['firstHungerTick']
            if lead > assertions['hungerToleranceTicks']:
                raise TestFailure('%s arena: the hunger debuff showed up %d ticks before the '
                                  'first damage (tolerance %d): the cancelled call reported '
                                  'success, so the vanilla override fired its debuff ahead of '
                                  'the hit' % (name, lead, assertions['hungerToleranceTicks']))

    print('      deferral: %d ticks (baseline %d, long-swing %d; swing durations %d/%d)'
          % (deferral, baseline['firstHitTick'], long_swing['firstHitTick'],
             VANILLA_SWING, FATIGUE_SWING))


def check_multiplier(states, assertions):
    scaled = by_name(states, MULTIPLIER_SITE)
    vanilla = by_name(states, VANILLA_SITE)

    if vanilla['firstHitTick'] is None:
        raise TestFailure('%s arena (no config entry, expected vanilla timing) never took '
                          'damage within %d ticks'
                          % (VANILLA_SITE, vanilla['windowTicks']))
    if vanilla['firstHitTick'] > assertions['maxVanillaFirstHitTick']:
        raise TestFailure('%s arena (no config entry) landed its first hit at tick %d, above '
                          'the %d tick bound: an entity without an entry must keep vanilla '
                          'timing' % (VANILLA_SITE, vanilla['firstHitTick'],
                                      assertions['maxVanillaFirstHitTick']))
    if scaled['firstHitTick'] is None:
        raise TestFailure('%s arena (multiplier x%g) never took damage within %d ticks: the '
                          'scaled deferral never elapsed'
                          % (MULTIPLIER_SITE, MULTIPLIER, scaled['windowTicks']))
    if scaled['firstHitTick'] < assertions['minScaledFirstHitTick']:
        raise TestFailure('%s arena (multiplier x%g) landed its first hit at tick %d, below '
                          'the %d tick bound: the configured multiplier is not being applied'
                          % (MULTIPLIER_SITE, MULTIPLIER, scaled['firstHitTick'],
                             assertions['minScaledFirstHitTick']))

    separation = scaled['firstHitTick'] - vanilla['firstHitTick']
    if separation < assertions['minTypeSeparationTicks']:
        raise TestFailure('the scaled husk landed only %d ticks after the unconfigured zombie '
                          '(expected at least %d): per-entity multipliers are not independent'
                          % (separation, assertions['minTypeSeparationTicks']))

    print('      husk x%g first hit %d, unconfigured zombie first hit %d, separation %d ticks'
          % (MULTIPLIER, scaled['firstHitTick'], vanilla['firstHitTick'], separation))
    print('      the config was rewritten while the server ran, so the reload took effect '
          'without a restart')


def check_kept(states, assertions):
    state = by_name(states, KEPT_SITE)
    if state['firstHitTick'] is None:
        raise TestFailure('%s arena never took damage within %d ticks after a broken config '
                          'was written' % (KEPT_SITE, state['windowTicks']))
    if state['firstHitTick'] < assertions['minScaledFirstHitTick']:
        raise TestFailure('%s arena landed its first hit at tick %d, below the %d tick bound: '
                          'a broken config file dropped the previously loaded multipliers '
                          'instead of keeping them' % (KEPT_SITE, state['firstHitTick'],
                                                       assertions['minScaledFirstHitTick']))
    print('      broken config kept the previous x%g multipliers (first hit %d)'
          % (MULTIPLIER, state['firstHitTick']))


def check_fallback(states, assertions):
    state = by_name(states, FALLBACK_SITE)
    if state['firstHitTick'] is None:
        raise TestFailure('%s arena never took damage within %d ticks after the config was '
                          'deleted' % (FALLBACK_SITE, state['windowTicks']))
    if state['firstHitTick'] > assertions['maxVanillaFirstHitTick']:
        raise TestFailure('%s arena landed its first hit at tick %d, above the %d tick bound: '
                          'deleting the config did not fall back to vanilla timing'
                          % (FALLBACK_SITE, state['firstHitTick'],
                             assertions['maxVanillaFirstHitTick']))
    print('      deleted config fell back to vanilla timing (first hit %d)' % state['firstHitTick'])


def check_reach(states, assertions):
    control = by_name(states, 'reach-control')
    escape = by_name(states, 'reach-escape')

    if not escape['escapedAt']:
        raise TestFailure('the villager was never moved out of reach: the escape step did not '
                          'run, so this scenario proves nothing')
    if control['hits'] < assertions['minReachControlHits']:
        raise TestFailure('reach-control arena took no damage in %d ticks: its husk never '
                          'swung, so the escape arena cannot be judged either'
                          % control['observedTicks'])
    if escape['firstHitTick'] is not None:
        raise TestFailure('the villager was moved out of melee reach %d ticks into a %d tick '
                          'windup, yet the deferred hit still landed at tick %d: the hit is '
                          'not re-checking reach when it resolves'
                          % (assertions['escapeAtTick'], FATIGUE_SWING, escape['firstHitTick']))

    print('      control husk (villager stayed) hit at %d; the escaped villager stayed unharmed '
          'for the %d ticks that were observed, i.e. past the control hit plus %d ticks of grace'
          % (control['firstHitTick'], escape['observedTicks'], assertions['escapeGraceTicks']))


CHECKS = {
    'deferral': check_deferral,
    'multiplier': check_multiplier,
    'broken-config': check_kept,
    'deleted-config': check_fallback,
    'reach': check_reach,
}


def main():
    parser = argparse.ArgumentParser(description='StrikeAfterSwing behaviour test')
    parser.add_argument('--target', required=True,
                        help='target directory name, e.g. forge-1.20.1')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--password', default='behaviortest')
    parser.add_argument('--port', type=int, default=None)
    parser.add_argument('--scenarios', default=None,
                        help='comma separated subset of scenario names to run')
    parser.add_argument('--keep-running', action='store_true',
                        help='do not send "stop" to the server when finished')
    args = parser.parse_args()

    settings = load_settings(args.target)
    assertions = dict(DEFAULT_ASSERTIONS)
    assertions.update(settings.get('assertions', {}))
    port = args.port or settings.get('rconPort', 25575)

    scenarios = SCENARIOS
    if args.scenarios:
        wanted = [name.strip() for name in args.scenarios.split(',') if name.strip()]
        scenarios = [scenario for scenario in SCENARIOS if scenario['name'] in wanted]
        missing = [name for name in wanted
                   if name not in [scenario['name'] for scenario in SCENARIOS]]
        if missing:
            print('unknown scenario(s): %s' % ', '.join(missing))
            return 2

    print('== behaviour test: %s' % args.target)
    print('    rcon port %d, scenarios: %s'
          % (port, ', '.join(scenario['name'] for scenario in scenarios)))

    path = config_path(args.target)
    connection = None
    failures = []
    try:
        if not rcon.wait_for_port(args.host, port, assertions['pollTimeoutSeconds']):
            raise TestFailure('RCON port %d did not open' % port)

        connection = rcon.Rcon(args.host, port, args.password)
        for command in settings['worldSetup']:
            require_command(connection, command)
        wait_for_tick_rate(connection, settings, assertions)

        for scenario in scenarios:
            states = run_scenario(connection, settings, assertions, scenario, path)
            CHECKS[scenario['name']](states, assertions)

        print('')
        print('    RESULT: PASS (%d scenarios)'
              % len(scenarios))
    except (TestFailure, rcon.RconError) as error:
        failures.append(str(error))
        print('')
        print('    RESULT: FAIL - %s' % error)
        # GitHub Actions turns "::error::" lines into check annotations, which keeps the
        # failure readable from outside the job log (that endpoint needs a token).
        print('::error title=behaviour test %s::%s' % (args.target, error))
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
        # Never leave a config file behind that a later run would inherit as its starting
        # state; the mod recreates its default when the file is missing.
        try:
            apply_config(path, DELETED)
        except OSError as error:
            print('    could not clean up %s: %s' % (path, error))

    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
