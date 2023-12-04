#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# Add the Cloud SDK distribution URI as a package source
echo "Adding the Cloud SDK distribution URI as a package source..."
echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] http://packages.cloud.google.com/apt cloud-sdk main" | sudo tee -a /etc/apt/sources.list.d/google-cloud-sdk.list

# Import the Google Cloud public key
echo "Importing the Google Cloud public key..."
curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key --keyring /usr/share/keyrings/cloud.google.gpg add -

# Update the package list and install the Cloud SDK
echo "Updating the package list and installing the Google Cloud SDK..."
sudo apt-get update && sudo apt-get install google-cloud-sdk -y

echo "Google Cloud SDK installation completed."


