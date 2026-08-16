#!/bin/bash
# Runs inside the LocalStack container once S3 is ready.
set -e

BUCKET="inventory-local"

awslocal s3api create-bucket --bucket "$BUCKET"

# The frontend uploads product images directly via presigned PUT, so the bucket
# needs CORS for the Vite dev origin. Keep this in sync with app.cors.allowed-origins.
awslocal s3api put-bucket-cors --bucket "$BUCKET" --cors-configuration '{
  "CORSRules": [
    {
      "AllowedOrigins": ["http://localhost:5173"],
      "AllowedMethods": ["GET", "PUT", "HEAD"],
      "AllowedHeaders": ["*"],
      "ExposeHeaders": ["ETag"],
      "MaxAgeSeconds": 3000
    }
  ]
}'

echo "LocalStack ready: bucket '$BUCKET' created with CORS."
