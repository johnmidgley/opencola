# OpenCola State Export Tool

A one-off migration tool that reads the original OpenCola (Kotlin/JVM) storage and exports transaction data in a neutral JSON format for import into the new Rust-based OpenCola.

## What It Does

- Reads the address book (personas and peers) from the original OpenCola storage
- Verifies transaction chain integrity (hash chain continuity + ECDSA signature validation)
- Exports transactions in a JSON format compatible with the Rust `Value` enum serde format
- Copies referenced data files (images, attachments) from the content-addressed file store
- Provides a web UI for browsing, selecting, and exporting data interactively

## Building

From the `opencola-server` directory:

```sh
./gradlew :tools:export-state:build
```

## Running

```sh
./gradlew :tools:export-state:run
```

The tool auto-detects the storage directory:

| Platform | Default Path |
|----------|-------------|
| macOS    | `~/Library/Application Support/OpenCola/storage` |
| Linux    | `~/.opencola/storage` |
| Windows  | `%LOCALAPPDATA%\OpenCola\storage` |

If the keystore has a non-default password, you will be prompted securely at startup (input is hidden).

### Options

```
--storage-path, -s <path>  Path to OpenCola storage directory
--port <port>              Web server port (default: 8090)
--output, -o <path>        Default export output directory (default: ./export)
--help, -h                 Show this help
```

### Example

```sh
./gradlew :tools:export-state:run --args="-s /path/to/storage --port 9000"
```

Then open http://localhost:9000 in your browser.

## Web UI

The web interface has three main views:

**Address Book** - Lists all personas (local identities with private keys) and peers (remote contacts). Click any entry to inspect it.

**Authority Detail** - For a selected persona or peer:
- **Verify Chain** - Validates that every transaction ID correctly hashes from the previous signed transaction, and that all signatures are valid against the authority's public key.
- **Browse Transactions** - Paginated list of transactions with expandable details showing entities, attributes, values, and operations. Each transaction has a checkbox for selective export.
- **View Entities** - Summary of all entities with their current state (name, type, URI, description).

**Export** - Choose "Export All" or "Export Selected", set an output path, and the tool writes the export files.

## Export Format

The export produces three things:

### `export-manifest.json`

Metadata about the export including authority info, counts, and any errors encountered.

```json
{
  "export_version": 1,
  "source": "opencola-kotlin",
  "exported_at": "2026-02-17T12:00:00Z",
  "authority": {
    "id": "<base58-id>",
    "name": "Persona Name",
    "public_key": "<hex-encoded>"
  },
  "transaction_count": 42,
  "data_file_count": 15
}
```

### `transactions.json`

Array of signed transactions. Values use a tagged format matching the Rust `Value` enum (`#[serde(tag = "type", content = "value")]`):

```json
[
  {
    "id": "<base58-transaction-id>",
    "authority_id": "<base58-authority-id>",
    "epoch_second": 1708083600,
    "timestamp": "2024-02-16T15:00:00Z",
    "signature": "<hex-encoded>",
    "signature_algorithm": "SHA3-256withECDSA",
    "entities": [
      {
        "entity_id": "<base58-entity-id>",
        "facts": [
          {
            "attribute": "Name",
            "value": { "type": "String", "value": "Example" },
            "operation": "Add"
          }
        ]
      }
    ]
  }
]
```

**Value type mapping:**

| Kotlin Type | JSON `type` | JSON `value` |
|-------------|-------------|--------------|
| String      | `String`    | string       |
| Boolean     | `Boolean`   | bool         |
| Int         | `Int`       | number       |
| Float       | `Float`     | number       |
| URI         | `Uri`       | string       |
| Id          | `Id`        | base58 string |
| ByteArray   | `ByteArray` | hex string   |
| PublicKey   | `PublicKey`  | hex string   |
| Empty       | `Empty`     | (absent)     |

### `data/`

Content files copied from the content-addressed file store, preserving the 2-character prefix directory structure:

```
export/
├── export-manifest.json
├── transactions.json
└── data/
    ├── ab/
    │   └── cdef1234...
    └── 7f/
        └── 9a8b7c6d...
```

## Chain Verification

The verifier checks two things for each transaction in the chain:

1. **Hash chain continuity** - The first transaction ID must equal `SHA256("{authorityId}.firstTransaction")`. Each subsequent ID must equal `SHA256(encodeProto(previousSignedTransaction))`.

2. **Signature validity** - Each signed transaction's ECDSA signature is verified against the authority's public key from the address book.

The verification report includes total transaction count, valid/invalid signature counts, whether the chain is intact, and a list of any specific issues found.

## API Endpoints

The web UI communicates via these JSON endpoints:

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/api/personas` | List all personas |
| `GET`  | `/api/peers` | List all peers |
| `GET`  | `/api/authority/{id}/verify` | Verify transaction chain |
| `GET`  | `/api/authority/{id}/transactions?offset=0&limit=50` | Paginated transactions |
| `GET`  | `/api/authority/{id}/entities` | List entities with current state |
| `POST` | `/api/authority/{id}/export` | Export transactions and data files |

The export endpoint accepts a JSON body:

```json
{
  "output_path": "export/my-persona",
  "transaction_ids": ["<id1>", "<id2>"]
}
```

Omit `transaction_ids` to export all transactions.

## Project Structure

```
tools/export-state/
├── build.gradle.kts
└── src/main/
    ├── kotlin/io/opencola/tools/export/
    │   ├── Main.kt            # CLI entry point and password handling
    │   ├── StorageAccess.kt   # Read-only access to original storage
    │   ├── ChainVerifier.kt   # Transaction chain verification
    │   ├── Exporter.kt        # JSON export and data file copying
    │   └── ExportServer.kt    # Ktor web server with API routes
    └── resources/
        ├── web/index.html     # Single-page web UI
        └── logback.xml        # Logging configuration
```
