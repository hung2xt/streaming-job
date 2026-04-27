# Configuration Files

## ⚠️ Security Warning

**DO NOT commit credentials to version control!**

Before running the pipeline, you must configure your own credentials.

## Setup Instructions

### 1. Configure S3/MinIO Credentials

The `core-site.xml` file contains placeholder credentials that **must be replaced**.

**Option A: Edit the existing file**
```bash
nano core-site.xml
```

Replace:
- `YOUR_ACCESS_KEY_HERE` → Your actual access key
- `YOUR_SECRET_KEY_HERE` → Your actual secret key

**Option B: Use the template**
```bash
# Copy template to active config
cp core-site.xml.template core-site.xml

# Edit with your credentials
nano core-site.xml
```

### 2. Default Credentials

**For local MinIO:**
```xml
<property>
    <name>fs.s3a.access.key</name>
    <value>minioadmin</value>
</property>
<property>
    <name>fs.s3a.secret.key</name>
    <value>minioadmin</value>
</property>
```

**For AWS S3:**
- Use your IAM access key and secret key
- Update endpoint to `s3.amazonaws.com` or regional endpoint
- Set `fs.s3a.path.style.access` to `false`
- Set `fs.s3a.connection.ssl.enabled` to `true`

### 3. Verify Configuration

After updating credentials, verify the file:

```bash
# Check that placeholders are replaced
grep "YOUR_" core-site.xml
# Should return nothing if properly configured
```

## Files in This Directory

| File | Purpose | Commit to Git? |
|------|---------|----------------|
| `flink-conf.yaml` | Flink cluster configuration | ✅ Yes (no secrets) |
| `core-site.xml` | S3A credentials (**CONTAINS SECRETS**) | ❌ **NO - Add to .gitignore** |
| `core-site.xml.template` | Template for core-site.xml | ✅ Yes (no secrets) |
| `README.md` | This file | ✅ Yes |

## Security Best Practices

1. **Never commit credentials** to version control
2. **Use environment variables** in production:
   ```bash
   export AWS_ACCESS_KEY_ID=your-key
   export AWS_SECRET_ACCESS_KEY=your-secret
   ```

3. **Use IAM roles** when running on AWS EC2/ECS
4. **Rotate credentials** regularly
5. **Use separate credentials** for dev/staging/production

## Troubleshooting

### Error: "Access Denied" or "403 Forbidden"

**Problem:** Wrong credentials in core-site.xml

**Solution:**
1. Verify credentials are correct
2. Check MinIO/S3 permissions
3. For MinIO, verify user has read/write access to bucket

### Error: "YOUR_ACCESS_KEY_HERE not found"

**Problem:** Forgot to replace placeholders

**Solution:**
```bash
# Edit core-site.xml and replace all YOUR_*_HERE values
nano core-site.xml
```

## Need Help?

See the main [TROUBLESHOOTING.md](../docs/TROUBLESHOOTING.md) for more issues and solutions.
