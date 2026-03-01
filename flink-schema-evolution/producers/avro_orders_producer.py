#!/usr/bin/env python3
"""
Avro Orders Producer with Schema Evolution Demo

This producer demonstrates schema evolution by:
1. Sending orders with schema v1 (6 fields) for the first 50 messages
2. Evolving to schema v2 (7 fields with shipping_address) for subsequent messages

The Schema Registry automatically handles schema registration and versioning.
"""

import json
import random
import time
import uuid
from datetime import datetime
from confluent_kafka import Producer
from confluent_kafka.serialization import SerializationContext, MessageField
from confluent_kafka.schema_registry import SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroSerializer

# Configuration
KAFKA_BOOTSTRAP_SERVERS = "localhost:9093"
SCHEMA_REGISTRY_URL = "http://localhost:8081"
TOPIC = "orders_avro"

# Schema v1 - Initial schema with 6 fields
SCHEMA_V1 = """{
    "type": "record",
    "name": "Order",
    "namespace": "com.example.avro",
    "fields": [
        {"name": "order_id", "type": "string"},
        {"name": "customer_id", "type": "long"},
        {"name": "product_id", "type": "string"},
        {"name": "quantity", "type": "int"},
        {"name": "total_amount", "type": "double"},
        {"name": "order_time", "type": {"type": "long", "logicalType": "timestamp-millis"}}
    ]
}"""

# Schema v2 - Evolved schema with shipping_address field
SCHEMA_V2 = """{
    "type": "record",
    "name": "Order",
    "namespace": "com.example.avro",
    "fields": [
        {"name": "order_id", "type": "string"},
        {"name": "customer_id", "type": "long"},
        {"name": "product_id", "type": "string"},
        {"name": "quantity", "type": "int"},
        {"name": "total_amount", "type": "double"},
        {"name": "order_time", "type": {"type": "long", "logicalType": "timestamp-millis"}},
        {"name": "shipping_address", "type": ["null", "string"], "default": null}
    ]
}"""

# Schema v3 - Further evolved with payment_method and discount_percent
SCHEMA_V3 = """{
    "type": "record",
    "name": "Order",
    "namespace": "com.example.avro",
    "fields": [
        {"name": "order_id", "type": "string"},
        {"name": "customer_id", "type": "long"},
        {"name": "product_id", "type": "string"},
        {"name": "quantity", "type": "int"},
        {"name": "total_amount", "type": "double"},
        {"name": "order_time", "type": {"type": "long", "logicalType": "timestamp-millis"}},
        {"name": "shipping_address", "type": ["null", "string"], "default": null},
        {"name": "payment_method", "type": ["null", "string"], "default": null},
        {"name": "discount_percent", "type": ["null", "double"], "default": null}
    ]
}"""

# Sample data
PRODUCTS = ["LAPTOP-001", "PHONE-002", "TABLET-003", "HEADPHONES-004", "MONITOR-005"]
PAYMENT_METHODS = ["CREDIT_CARD", "DEBIT_CARD", "PAYPAL", "BANK_TRANSFER", "CRYPTO"]
ADDRESSES = [
    "123 Main St, New York, NY 10001",
    "456 Oak Ave, Los Angeles, CA 90001",
    "789 Pine Rd, Chicago, IL 60601",
    "321 Elm Blvd, Houston, TX 77001",
    "654 Maple Dr, Phoenix, AZ 85001"
]


def delivery_report(err, msg):
    """Callback for message delivery reports."""
    if err is not None:
        print(f"Message delivery failed: {err}")
    else:
        print(f"Message delivered to {msg.topic()} [{msg.partition()}] @ offset {msg.offset()}")


def create_order_v1():
    """Create an order using schema v1 (without shipping_address)."""
    return {
        "order_id": str(uuid.uuid4()),
        "customer_id": random.randint(1000, 9999),
        "product_id": random.choice(PRODUCTS),
        "quantity": random.randint(1, 10),
        "total_amount": round(random.uniform(10.0, 1000.0), 2),
        "order_time": int(datetime.now().timestamp() * 1000)
    }


def create_order_v2():
    """Create an order using schema v2 (with shipping_address)."""
    order = create_order_v1()
    order["shipping_address"] = random.choice(ADDRESSES)
    return order


def create_order_v3():
    """Create an order using schema v3 (with payment_method and discount_percent)."""
    order = create_order_v2()
    order["payment_method"] = random.choice(PAYMENT_METHODS)
    order["discount_percent"] = round(random.uniform(0, 30), 2) if random.random() > 0.3 else None
    return order


def main():
    """Main producer function demonstrating schema evolution."""

    print("=" * 60)
    print("Avro Orders Producer - Schema Evolution Demo")
    print("=" * 60)

    # Initialize Schema Registry client
    schema_registry_conf = {"url": SCHEMA_REGISTRY_URL}
    schema_registry_client = SchemaRegistryClient(schema_registry_conf)

    # Initialize Kafka producer
    producer_conf = {
        "bootstrap.servers": KAFKA_BOOTSTRAP_SERVERS,
        "client.id": "avro-orders-producer"
    }
    producer = Producer(producer_conf)

    # Create serializers for all schema versions
    avro_serializer_v1 = AvroSerializer(
        schema_registry_client,
        SCHEMA_V1,
        lambda obj, ctx: obj
    )

    avro_serializer_v2 = AvroSerializer(
        schema_registry_client,
        SCHEMA_V2,
        lambda obj, ctx: obj
    )

    avro_serializer_v3 = AvroSerializer(
        schema_registry_client,
        SCHEMA_V3,
        lambda obj, ctx: obj
    )

    message_count = 0
    schema_evolved_v2 = False
    schema_evolved_v3 = False

    print(f"\nProducing messages to topic: {TOPIC}")
    print(f"Schema Registry: {SCHEMA_REGISTRY_URL}")
    print("-" * 60)

    try:
        while True:
            message_count += 1

            # Schema evolution timeline:
            # Messages 1-50:   v1 (6 fields)
            # Messages 51-100: v2 (7 fields - adds shipping_address)
            # Messages 101+:   v3 (9 fields - adds payment_method, discount_percent)

            if message_count <= 50:
                order = create_order_v1()
                serializer = avro_serializer_v1
                schema_version = "v1"
            elif message_count <= 100:
                if not schema_evolved_v2:
                    print("\n" + "=" * 60)
                    print("SCHEMA EVOLUTION v1 -> v2: Adding shipping_address")
                    print("=" * 60 + "\n")
                    schema_evolved_v2 = True

                order = create_order_v2()
                serializer = avro_serializer_v2
                schema_version = "v2"
            else:
                if not schema_evolved_v3:
                    print("\n" + "=" * 60)
                    print("SCHEMA EVOLUTION v2 -> v3: Adding payment_method, discount_percent")
                    print("=" * 60 + "\n")
                    schema_evolved_v3 = True

                order = create_order_v3()
                serializer = avro_serializer_v3
                schema_version = "v3"

            # Serialize and send
            serialization_context = SerializationContext(TOPIC, MessageField.VALUE)
            serialized_value = serializer(order, serialization_context)

            producer.produce(
                topic=TOPIC,
                key=order["order_id"].encode("utf-8"),
                value=serialized_value,
                callback=delivery_report
            )

            # Print order details
            extra_info = ""
            if "shipping_address" in order and order.get("shipping_address"):
                extra_info += f" | Addr: {order['shipping_address'][:15]}..."
            if "payment_method" in order and order.get("payment_method"):
                extra_info += f" | Pay: {order['payment_method']}"
            if "discount_percent" in order and order.get("discount_percent"):
                extra_info += f" | Disc: {order['discount_percent']}%"

            print(f"[{schema_version}] Order #{message_count}: {order['order_id'][:8]}... "
                  f"| Product: {order['product_id']} "
                  f"| Amount: ${order['total_amount']:.2f}{extra_info}")

            producer.poll(0)
            time.sleep(1)  # Send one message per second

    except KeyboardInterrupt:
        print("\n\nShutting down producer...")
    finally:
        # Flush remaining messages
        producer.flush()
        print(f"Total messages sent: {message_count}")
        print("Producer shutdown complete.")


if __name__ == "__main__":
    main()
