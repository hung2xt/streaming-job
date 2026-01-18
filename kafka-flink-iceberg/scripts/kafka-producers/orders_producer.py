#!/usr/bin/env python3
"""
Kafka Orders Producer
Generates synthetic order data for testing the Flink pipeline
"""

import json
import random
import time
from datetime import datetime
from kafka import KafkaProducer

# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS = ['localhost:9093']
TOPIC_NAME = 'orders'

# Sample Data
PRODUCTS = [
    ('PROD-001', 'Laptop', 1200.00),
    ('PROD-002', 'Mouse', 25.00),
    ('PROD-003', 'Keyboard', 75.00),
    ('PROD-004', 'Monitor', 350.00),
    ('PROD-005', 'Headphones', 150.00),
    ('PROD-006', 'Webcam', 80.00),
    ('PROD-007', 'Desk Chair', 300.00),
    ('PROD-008', 'Standing Desk', 450.00),
    ('PROD-009', 'USB Cable', 12.00),
    ('PROD-010', 'Power Bank', 45.00),
]

STATUSES = ['pending', 'confirmed', 'shipped', 'delivered']
CUSTOMER_IDS = list(range(1001, 1021))  # 20 customers

def generate_order():
    """Generate a random order"""
    product_id, product_name, base_price = random.choice(PRODUCTS)
    quantity = random.randint(1, 5)
    total_amount = round(base_price * quantity, 2)

    order = {
        'order_id': f"ORD-{int(time.time() * 1000)}",
        'customer_id': random.choice(CUSTOMER_IDS),
        'product_id': product_id,
        'product_name': product_name,
        'quantity': quantity,
        'total_amount': total_amount,
        'order_time': datetime.now().isoformat(),
        'status': random.choice(STATUSES)
    }
    return order

def main():
    """Main producer loop"""
    print("=" * 60)
    print("📦 Kafka Orders Producer")
    print("=" * 60)
    print(f"Kafka Servers: {KAFKA_BOOTSTRAP_SERVERS}")
    print(f"Topic: {TOPIC_NAME}")
    print(f"Products: {len(PRODUCTS)}")
    print(f"Customers: {len(CUSTOMER_IDS)}")
    print("=" * 60)

    # Create Kafka producer
    try:
        producer = KafkaProducer(
            bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            acks='all',  # Wait for all replicas
            retries=3
        )
        print("✅ Connected to Kafka")
    except Exception as e:
        print(f"❌ Failed to connect to Kafka: {e}")
        return

    print("\n🚀 Producing orders... (Ctrl+C to stop)")
    print("-" * 60)

    count = 0
    try:
        while True:
            order = generate_order()

            # Send to Kafka
            future = producer.send(TOPIC_NAME, value=order)
            future.get(timeout=10)  # Wait for acknowledgment

            count += 1
            if count % 10 == 0:
                print(f"📊 Produced {count} orders | Latest: {order['order_id']} | "
                      f"Customer: {order['customer_id']} | "
                      f"Product: {order['product_name']} | "
                      f"Amount: ${order['total_amount']:.2f}")

            # Sleep for realistic rate (1-3 seconds)
            time.sleep(random.uniform(1, 3))

    except KeyboardInterrupt:
        print("\n\n⏹️  Stopping producer...")
    except Exception as e:
        print(f"\n❌ Error: {e}")
    finally:
        producer.flush()
        producer.close()
        print(f"✅ Produced {count} total orders")
        print("=" * 60)

if __name__ == '__main__':
    main()
