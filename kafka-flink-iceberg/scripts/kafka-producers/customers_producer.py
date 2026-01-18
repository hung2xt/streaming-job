#!/usr/bin/env python3
"""
Kafka Customers Producer
Generates synthetic customer data for testing the Flink pipeline
"""

import json
import random
import time
from kafka import KafkaProducer

# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS = ['localhost:9093']
TOPIC_NAME = 'customers'

# Sample Data
FIRST_NAMES = ['Alice', 'Bob', 'Charlie', 'Diana', 'Eve', 'Frank', 'Grace', 'Henry',
               'Ivy', 'Jack', 'Kate', 'Leo', 'Maria', 'Noah', 'Olivia', 'Paul',
               'Quinn', 'Rachel', 'Sam', 'Tina']

LAST_NAMES = ['Smith', 'Johnson', 'Williams', 'Brown', 'Jones', 'Garcia', 'Miller',
              'Davis', 'Rodriguez', 'Martinez', 'Wilson', 'Anderson', 'Taylor',
              'Thomas', 'Moore', 'Jackson', 'Martin', 'Lee', 'White', 'Harris']

CITIES = ['New York', 'Los Angeles', 'Chicago', 'Houston', 'Phoenix', 'Philadelphia',
          'San Antonio', 'San Diego', 'Dallas', 'San Jose', 'Austin', 'Seattle',
          'Denver', 'Boston', 'Portland', 'Miami', 'Atlanta', 'Detroit', 'Minneapolis']

TIERS = ['bronze', 'silver', 'gold', 'platinum']
CUSTOMER_IDS = list(range(1001, 1021))  # 20 customers

def generate_customer(customer_id):
    """Generate customer data"""
    first_name = random.choice(FIRST_NAMES)
    last_name = random.choice(LAST_NAMES)

    customer = {
        'customer_id': customer_id,
        'name': f"{first_name} {last_name}",
        'email': f"{first_name.lower()}.{last_name.lower()}@example.com",
        'city': random.choice(CITIES),
        'tier': random.choice(TIERS),
        'loyalty_points': random.randint(0, 10000)
    }
    return customer

def main():
    """Main producer loop"""
    print("=" * 60)
    print("👤 Kafka Customers Producer")
    print("=" * 60)
    print(f"Kafka Servers: {KAFKA_BOOTSTRAP_SERVERS}")
    print(f"Topic: {TOPIC_NAME}")
    print(f"Customers: {len(CUSTOMER_IDS)}")
    print("=" * 60)

    # Create Kafka producer
    try:
        producer = KafkaProducer(
            bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
            key_serializer=lambda k: str(k).encode('utf-8'),
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            acks='all',
            retries=3
        )
        print("✅ Connected to Kafka")
    except Exception as e:
        print(f"❌ Failed to connect to Kafka: {e}")
        return

    print("\n🚀 Producing customer updates... (Ctrl+C to stop)")
    print("-" * 60)

    count = 0
    try:
        while True:
            # Pick a random customer to update
            customer_id = random.choice(CUSTOMER_IDS)
            customer = generate_customer(customer_id)

            # Send to Kafka with customer_id as key
            future = producer.send(
                TOPIC_NAME,
                key=customer_id,
                value=customer
            )
            future.get(timeout=10)

            count += 1
            if count % 5 == 0:
                print(f"📊 Produced {count} updates | Customer: {customer['name']} (ID: {customer_id}) | "
                      f"City: {customer['city']} | Tier: {customer['tier']} | "
                      f"Points: {customer['loyalty_points']}")

            # Sleep for realistic rate (3-8 seconds)
            # Customers update less frequently than orders
            time.sleep(random.uniform(3, 8))

    except KeyboardInterrupt:
        print("\n\n⏹️  Stopping producer...")
    except Exception as e:
        print(f"\n❌ Error: {e}")
    finally:
        producer.flush()
        producer.close()
        print(f"✅ Produced {count} total customer updates")
        print("=" * 60)

if __name__ == '__main__':
    main()
