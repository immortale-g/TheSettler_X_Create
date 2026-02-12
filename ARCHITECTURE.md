# Architecture

## Purpose
TheSettler_X_Create extends MineColonies with a Create-based shop building that can fulfill requests using Create stock networks and warehouse racks. The mod adds a Create Shop building, custom request resolver logic, and helper blocks to integrate the Create logistics system with MineColonies deliveries.

## Core Concepts
- Create Shop building: a MineColonies building with its own request resolver that can fulfill requests from Create stock or racks.
- Output and pickup blocks: custom block entities used to coordinate deliveries and reservations.
- Request resolver: integrates with MineColonies request system to determine if the shop can satisfy a request and to create delivery requests.
- Create network facade: abstracts access to Create stock network inventory and package requests.
- Perma request: a curated, persistent request list for ore-tagged blocks, gated by building level.

## High-Level Flow
1. MineColonies issues a deliverable request.
2. Create Shop resolver checks eligibility, stock availability, and reservation state.
3. If available, it creates delivery requests and reserves stock.
4. Delivery completion or cancellation releases or updates reservations.

## Key Modules
- `com.thesettler_x_create.minecolonies.building.BuildingCreateShop`
  Handles building behavior, resolver setup, perma requests, and blueprint-related behavior.
- `com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver`
  Implements the Create Shop request resolution and delivery creation logic.
- `com.thesettler_x_create.create.CreateNetworkFacade`
  Encapsulates Create network summary and package request operations.
- `com.thesettler_x_create.blockentity.CreateShopBlockEntity`
  Stores reservation data and exposes item handlers for MineColonies interaction.
- `com.thesettler_x_create.minecolonies.requestsystem.CreateShopResolverInjector`
  Ensures shop resolvers are registered in the MineColonies request system.

## Data and State
- Reservations are tracked in `CreateShopBlockEntity` keyed by request UUID.
- Resolver state is instance-based per shop resolver to avoid cross-colony leaks.
- Cooldowns and debug logging gates live in `Config`.

## Configuration
- `Config` defines cooldowns, perma request level gating, debug logging flags, and related behavior.
- Debug logging is gated via `Config.DEBUG_LOGGING` and per-feature cooldowns.

## Assets and Blueprints
- Blueprints are stored in resources under `assets/structurize/scan` for jar distribution.
- Local scans reside in the MineColonies/Structurize scan directories during development.

## Build and Distribution
- Gradle builds the mod jar and includes resources from `src/main/resources`.
- MineColonies and Create are runtime dependencies and are expected on the client or server.

## Known Integration Points
- MineColonies request lifecycle callbacks are used to track delivery completion and cancellation.
- Create logistics API is used to query summaries and broadcast package requests.
