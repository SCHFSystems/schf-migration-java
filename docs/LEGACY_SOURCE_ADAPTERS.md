# Source Adapters

`SourceAdapter` is the only input boundary. Adapters return canonical records without leaking connections, credentials, absolute paths or source-specific schema into bundles.

Sprint 22F ships only `SyntheticSourceAdapter`. Future adapters belong in connector modules and require separate local security review and explicit authorization before real data access.
