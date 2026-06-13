# Nuit Interop

Compatibility layer for loading legacy custom sky resource packs through [Nuit](https://github.com/FlashyReese/nuit).

Nuit Interop currently supports:

- MCPatcher/OptiFine custom sky properties
- Legacy FabricSkyBoxes skybox JSON

The goal is to preserve legacy resource-pack behavior while letting Nuit remain the primary skybox renderer and API.

## Configuration

Nuit Interop provides in-game configuration options for:

- Enabling or disabling interoperability
- Processing OptiFine custom skies
- Processing MCPatcher custom skies
- Processing legacy FabricSkyBoxes skyboxes
- Preferring native Nuit skies when a resource pack already provides them
- Dumping converted sky data for debugging

## License

Nuit Interop is licensed under MIT. For more information, see the [license file](LICENSE).
