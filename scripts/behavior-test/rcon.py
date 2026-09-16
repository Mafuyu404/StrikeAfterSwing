#!/usr/bin/env python3
"""Minimal Minecraft RCON client plus the StrikeAfterSwing behaviour test.

The behaviour test drives a dedicated server through RCON and measures when a
mob's melee attack actually lands. See behavior-test.ps1 for the driver.
"""

import argparse
import json
import socket
import struct
import sys
import time

SERVERDATA_AUTH = 3
SERVERDATA_AUTH_RESPONSE = 2
SERVERDATA_EXECCOMMAND = 2
SERVERDATA_RESPONSE_VALUE = 0


class RconError(Exception):
    pass


class Rcon:
    def __init__(self, host, port, password, timeout=10.0):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.settimeout(timeout)
        self.next_id = 0
        self._login(password)

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass

    def _send(self, request_id, request_type, body):
        payload = struct.pack('<ii', request_id, request_type) + body.encode('utf-8') + b'\x00\x00'
        self.sock.sendall(struct.pack('<i', len(payload)) + payload)

    def _recv_exact(self, count):
        chunks = []
        remaining = count
        while remaining > 0:
            chunk = self.sock.recv(remaining)
            if not chunk:
                raise RconError('connection closed by server')
            chunks.append(chunk)
            remaining -= len(chunk)
        return b''.join(chunks)

    def _recv_packet(self):
        (length,) = struct.unpack('<i', self._recv_exact(4))
        payload = self._recv_exact(length)
        request_id, request_type = struct.unpack('<ii', payload[:8])
        body = payload[8:-2].decode('utf-8', errors='replace')
        return request_id, request_type, body

    def _login(self, password):
        self.next_id += 1
        request_id = self.next_id
        self._send(request_id, SERVERDATA_AUTH, password)
        while True:
            response_id, _, _ = self._recv_packet()
            # Some servers send an empty SERVERDATA_RESPONSE_VALUE before the auth reply.
            if response_id == request_id:
                return
            if response_id == -1:
                raise RconError('RCON authentication failed (wrong password?)')

    def command(self, command):
        """Runs one command and returns its response body."""
        self.next_id += 1
        request_id = self.next_id
        self._send(request_id, SERVERDATA_EXECCOMMAND, command)
        body = ''
        while True:
            response_id, _, chunk = self._recv_packet()
            if response_id != request_id:
                raise RconError('unexpected RCON packet id %r' % (response_id,))
            body += chunk
            if len(chunk) < 4096:
                return body


def parse_number(text):
    """Pulls the first float out of a command response."""
    for token in text.replace(':', ' ').split():
        try:
            return float(token.rstrip('fdbsL,'))
        except ValueError:
            continue
    raise RconError('no number found in response: %r' % text)


PARSE_ERROR_MARKERS = (
    '<--[here]',
    'unknown or incomplete command',
    'unknown command',
    'incorrect argument',
    'expected whitespace to end one argument',
    'expected literal',
    'expected integer',
    'expected float',
    'expected double',
    'expected string',
    'parse error',
    'cannot summon',
    'unable to summon',
    'failed to summon',
)

# Something went wrong even though the command was parsed correctly.
HARD_FAILURE_MARKERS = (
    'that position is not loaded',
    'unable to ',
    'failed to ',
    'cannot ',
    'no permission',
)

# The command was fine, it just had nothing to act on.
EMPTY_RESULT_MARKERS = (
    'no entity was found',
    'no entities were found',
    'no target was found',
    'no targets matched',
)


def require_ok(response, command, allow_empty=True):
    """Fails when the server answered a command with an error."""
    lowered = response.lower()
    for marker in PARSE_ERROR_MARKERS:
        if marker in lowered:
            raise RconError('command %r failed: %s' % (command, response.strip()))
    for marker in HARD_FAILURE_MARKERS:
        if marker in lowered:
            raise RconError('command %r failed: %s' % (command, response.strip()))
    if not allow_empty:
        for marker in EMPTY_RESULT_MARKERS:
            if marker in lowered:
                raise RconError('command %r found nothing: %s' % (command, response.strip()))
    return response

def run_script(rcon, commands, echo=True):
    """Runs a list of commands; returns a list of (command, response)."""
    results = []
    for command in commands:
        response = rcon.command(command)
        require_ok(response, command)
        results.append((command, response))
        if echo:
            print('    %-64s -> %s' % (command, response.strip()))
    return results


def wait_for_port(host, port, timeout):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            socket.create_connection((host, port), timeout=2.0).close()
            return True
        except OSError:
            time.sleep(0.5)
    return False
