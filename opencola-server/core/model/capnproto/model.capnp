@0xb38c382d763e68ad;
using Java = import "java.capnp";
$Java.package("io.opencola.model");
$Java.outerClassname("Transaction");
struct Id {
  bytes @0 :Data;
}

struct TransactionEntity {
  entityId @0 :Id;
  facts @1 :List(TransactionFact);
}

struct TransactionFact {
  attribute @0 :Attribute;
  value @1 :Value;
  operation @2 :Operation;
}

struct Attribute {
  name @0 :Text;
  uri @1 :Text;
  type @2 :AttributeType;
  codec @3 :Data;
  isIndexable @4 :Bool;
}

enum AttributeType {
  singleValue @0;
  multiValueSet @1;
  multiValueList @2;
}

struct Value {
  bytes @0 :Data;
}

enum Operation {
  add @0;
  retract @1;
}

struct Transaction {
  id @0 :Id;
  authorityId @1 :Id;
  transactionEntities @2 :List(TransactionEntity);
  epochSecond @3 :UInt64;
}