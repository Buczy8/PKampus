# infra/terraform — Oracle Always Free VM via Terraform (all free)

Provisions: VCN + subnet + security list (22/80/443) + 1x `VM.Standard.A1.Flex`
(2 OCPU / 12 GB, Ubuntu 24.04, 100 GB boot) + **reserved** public IP.

## 0. One-time OCI API key (Console -> Identity -> your user -> API keys)

```bash
mkdir -p ~/.oci && chmod 700 ~/.oci
openssl genrsa -out ~/.oci/pkampus-api.pem 2048 && chmod 600 ~/.oci/pkampus-api.pem
openssl rsa -pubout -in ~/.oci/pkampus-api.pem -out ~/.oci/pkampus-api.pub
# paste the .pub into the Console, copy tenancy/user OCIDs + fingerprint
```

Never commit `~/.oci/*` (root `.gitignore` already excludes `*.pem`).

## 1. Deploy infra

```bash
cd infra/terraform
export TF_VAR_tenancy_ocid=ocid1.tenancy.oc1..<...>
export TF_VAR_user_ocid=ocid1.user.oc1..<...>
export TF_VAR_fingerprint=<...>
export TF_VAR_private_key_path=$HOME/.oci/pkampus-api.pem
export TF_VAR_region=eu-frankfurt-1                 # your HOME region
export TF_VAR_ssh_public_key_path=$HOME/.ssh/pkampus-deploy.pub
export TF_VAR_domain=pkampus.me
terraform init
terraform plan    # free, creates nothing — review it
terraform apply   # ~2-4 min; note outputs below
```

If `apply` fails with "out of host capacity", retry another availability
domain later — standard Always Free behavior, not a config bug.

## 2. Outputs -> next steps

```bash
terraform output   # reserved_public_ip, api_url, ssh, next_dns_records
```

1. DNS: `A api -> <reserved_public_ip>` at the registrar (+ Resend records).
2. `infra/ansible`: fill `inventory.ini` + `vars/prod.yml`, run the playbook.
3. Idle-reclaim safety: Oracle may reclaim <20%-used Always Free VMs after
   7 days — a cheap uptime monitor hitting
   `https://api.<domain>/actuator/health` every few minutes keeps the
   instance visibly active (plus the GitHub `deploy-backend` workflow).
