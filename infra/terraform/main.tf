provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key_path = var.private_key_path
  region           = var.region
}

locals {
  compartment_id = var.compartment_ocid != "" ? var.compartment_ocid : var.tenancy_ocid
  name           = "pkampus-prod"
}

# --- Availability domain + Ubuntu 24.04 ARM image (looked up, never hardcoded) ---
data "oci_identity_availability_domains" "ads" {
  compartment_id = var.tenancy_ocid
}

data "oci_core_images" "ubuntu" {
  compartment_id           = local.compartment_id
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "24.04"
  shape                    = "VM.Standard.A1.Flex"
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

# --- Network: VCN + IGW + route + security list (22/80/443 in, all out) ---
resource "oci_core_vcn" "pkampus" {
  compartment_id = local.compartment_id
  cidr_block     = "10.0.0.0/16"
  display_name   = "${local.name}-vcn"
  dns_label      = "pkampus"
}

resource "oci_core_internet_gateway" "pkampus" {
  compartment_id = local.compartment_id
  vcn_id         = oci_core_vcn.pkampus.id
  display_name   = "${local.name}-igw"
}

resource "oci_core_route_table" "pkampus" {
  compartment_id = local.compartment_id
  vcn_id         = oci_core_vcn.pkampus.id
  display_name   = "${local.name}-rt"

  route_rules {
    destination       = "0.0.0.0/0"
    network_entity_id = oci_core_internet_gateway.pkampus.id
  }
}

resource "oci_core_security_list" "pkampus" {
  compartment_id = local.compartment_id
  vcn_id         = oci_core_vcn.pkampus.id
  display_name   = "${local.name}-seclist"

  egress_security_rules {
    destination = "0.0.0.0/0"
    protocol    = "all"
  }

  # SSH (narrow source to your IP in the Console after first login)
  ingress_security_rules {
    source   = "0.0.0.0/0"
    protocol = "6"
    tcp_options {
      min = 22
      max = 22
    }
  }

  # Caddy: HTTP (ACME challenge) + HTTPS
  ingress_security_rules {
    source   = "0.0.0.0/0"
    protocol = "6"
    tcp_options {
      min = 80
      max = 80
    }
  }

  ingress_security_rules {
    source   = "0.0.0.0/0"
    protocol = "6"
    tcp_options {
      min = 443
      max = 443
    }
  }
}

resource "oci_core_subnet" "pkampus" {
  compartment_id = local.compartment_id
  vcn_id         = oci_core_vcn.pkampus.id
  cidr_block     = "10.0.1.0/24"
  display_name   = "${local.name}-subnet"
  dns_label      = "api"
  route_table_id = oci_core_route_table.pkampus.id
  security_list_ids = [
    oci_core_security_list.pkampus.id,
  ]
}

# --- Compute: single Always Free Ampere A1 (2 OCPU / 12 GB) ---
resource "oci_core_instance" "api" {
  compartment_id      = local.compartment_id
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name
  display_name        = "${local.name}-api"
  shape               = "VM.Standard.A1.Flex"

  shape_config {
    ocpus         = 2
    memory_in_gbs = 12
  }

  source_details {
    source_type             = "image"
    source_id               = data.oci_core_images.ubuntu.images[0].id
    boot_volume_size_in_gbs = var.boot_volume_gb
  }

  metadata = {
    ssh_authorized_keys = file(var.ssh_public_key_path)
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.pkampus.id
    assign_public_ip = true
    display_name     = "${local.name}-vnic"
    hostname_label   = "api"
  }
}

# --- Reserved public IP (free while attached): survives stop/start, DNS-safe ---
data "oci_core_private_ips" "api" {
  ip_address = oci_core_instance.api.private_ip
  subnet_id  = oci_core_subnet.pkampus.id
}

resource "oci_core_public_ip" "api" {
  compartment_id = local.compartment_id
  display_name   = "${local.name}-ip"
  lifetime       = "RESERVED"
  private_ip_id  = data.oci_core_private_ips.api.private_ips[0].id
}
