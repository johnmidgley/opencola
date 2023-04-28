package io.opencola.serialization.protobuf

enum class ProtoType {
    ANY,
    DOUBLE,
    FLOAT,
    INT32,    // Uses variable-length encoding. Inefficient for encoding negative numbers - if your field is likely to
              // have negative values, use sint32 instead.
    INT64,    // Uses variable-length encoding. Inefficient for encoding negative numbers – if your field is likely to
              // have negative values, use sint64 instead.
    UINT32,   // Uses variable-length encoding.
    UINT64,   // Uses variable-length encoding.
    SINT32,   // Uses variable-length encoding. Signed int value. These more efficiently encode negative numbers than
              // regular int32s.
    SINT64,   // Uses variable-length encoding. Signed int value. These more efficiently encode negative numbers than
              // regular int64s.
    FIXED32,  // Always four bytes. More efficient than uint32 if values are often greater than 2^28.
    FIXED64,  // Always eight bytes. More efficient than uint64 if values are often greater than 2^56.
    SFIXED32, // Always four bytes.
    SFIXED64, // Always eight bytes.
    BOOL,
    STRING,   // A string must always contain UTF-8 encoded or 7-bit ASCII text.
    BYTES,    // May contain any arbitrary sequence of bytes.
}