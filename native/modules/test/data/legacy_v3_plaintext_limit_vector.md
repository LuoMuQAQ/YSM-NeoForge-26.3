# Historical v3 plaintext-limit vector

`legacy_v3_plaintext_limit_vector.ysm` was generated with the historical
`ysm-native` CityHash and ChaCha libraries and its dirty-Zstandard block-header
mapping. The plaintext is 134,217,729 zero bytes except for little-endian inner
version `1` at its start, so decoding must stop with `RESOURCE_LIMIT` one byte
past the frozen 128 MiB plaintext ceiling. The 4,197-byte envelope exercises
the limit without requiring a whole-plaintext fixture or a whole-plaintext
production allocation.
