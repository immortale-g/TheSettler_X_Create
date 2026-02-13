# ADR 001: Bridge Architecture

## Status
Accepted

## Context
MineColonies and Create expose public APIs that dictate the request resolution and logistics patterns
(resolver factories, tokenized serialization, network summaries, request handlers). A direct adapter
layer is required to integrate Create stock networks into MineColonies requests.

## Decision
Implement a bridge/adapter architecture that wraps MineColonies request resolution and Create logistics
access. The adapter is intentionally thin and follows API?mandated patterns to preserve compatibility
and reduce risk during upstream updates.

## Consequences
- The code structure resembles other mods integrating the same APIs.
- The adapter layer remains focused on Create Shop behavior (perma requests, belt handling, pickup/output
  reservations) rather than rewriting the MineColonies request system.
- Upstream API changes can be addressed locally in adapter components without redesigning the mod.
