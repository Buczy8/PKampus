# infra/terraform-azure — cheap 24/7 VM on the student credit (~$15/mo)

Provisions: resource group + VNet + NSG (22/80/443) + `Standard_B2ts_v2`
(2 vCPU / 1 GB, Ubuntu 24.04, 32 GB Standard HDD) + **static** public IP.
$88 ≈ 6 months at 24/7. `B1ls` (0.5 GB) is NOT an option — Spring Boot's
boot-time peak (Flyway + Hibernate) needs ~700 MB; see RAM math in chat.

## 0. Azure CLI + login (laptop, once)

```bash
# https://learn.microsoft.com/cli/azure/install-azure-cli (apt repo, needs sudo)
az login
export ARM_SUBSCRIPTION_ID=<student subscription id>   # az account list -o table
```

The `azurerm` provider authenticates via this CLI session — no service
principal, no keys to manage.

## 1. Deploy infra

```bash
cd infra/terraform-azure
export TF_VAR_subscription_id=$ARM_SUBSCRIPTION_ID
export TF_VAR_location=polandcentral
export TF_VAR_ssh_public_key_path=$HOME/.ssh/pkampus-deploy.pub
export TF_VAR_domain=pkampus.me
terraform init
terraform plan    # free, creates nothing — review it
terraform apply   # ~3-5 min; note outputs below
```

If `plan` says `B2ts_v2` is unavailable in Poland Central, set
`TF_VAR_vm_size=Standard_B1s` (same price, 1 vCPU) and re-plan.

## 2. Outputs -> next steps

```bash
terraform output   # public_ip, api_url, ssh, next_dns_records
```

1. DNS: `A api -> <public_ip>` at the registrar (+ Resend records already Verified).
2. `infra/ansible`: `inventory.ini` -> new IP, `vars/prod.yml` unchanged, run the playbook.
3. Old Oracle experiment (`infra/terraform/`) stays dormant — run `terraform
   destroy` there only if you ever created anything (empty state = nothing).
4. Money guard: student subscription has a spending cap — credit exhaustion
   stops services instead of billing. Watch Portal -> Cost Management.
