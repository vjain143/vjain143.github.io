🚀 From Static Trino ACLs to a Policy-Driven Future

Author: Vivek Jain

⸻

🌍 Introduction

Trino’s native ACL JSON access-control mechanism works for small setups, but as data platforms evolve into multi-cluster, multi-domain data meshes, static JSON files quickly become a bottleneck.

They lack:
	•	🔒 Auditability
	•	⚡ Flexibility for fine-grained access
	•	🧩 Delegation to domain teams
	•	🚀 Dynamic attribute-based rules (ABAC)

That’s why we’re transitioning to a policy engine (e.g., OPA / Open Policy Agent) — where access control is centralized, GitOps-driven, and dynamic.

⸻

🏁 The Starting Point: ACL JSON

Trino ACL JSON is static and cluster-local:

{
  "catalogs": {
    "cosmos": {
      "allow": [
        {
          "principal": "finance-analyst",
          "privileges": ["SELECT"],
          "schemas": ["transactions"],
          "tables": ["payments"]
        }
      ]
    }
  }
}

✅ Works fine in single cluster setups.
❌ Doesn’t scale for multi-domain federated meshes.

⸻

🔑 The Transition Journey (Mira Stories)

We framed the transition as Mira-style user stories to align platform, security, and compliance goals.

⸻

Phase 1 — Baseline Migration
	•	As a Data Platform Engineer, I want to replace static ACL JSON files with a central policy engine, so that access control is consistent and auditable across clusters.
	•	Mirror ACL JSON → OPA policies (Rego).

package trino.authz

allow {
  input.principal == "finance-analyst"
  input.catalog == "cosmos"
  input.schema == "transactions"
  input.table == "payments"
  input.privilege == "SELECT"
}


⸻

Phase 2 — Human-Readable & GitOps
	•	As a Security Lead, I want to define policies in human-readable language instead of rigid JSON rules.
	•	Policies managed in Git repos, deployed via ArgoCD/Flux.

⸻

Phase 3 — Fine-Grained Controls
	•	As a Trino Administrator, I want to enforce permissions at catalog, schema, table, view, and function levels.
	•	Add CI/CD testing harness for policy changes.

⸻

Phase 4 — Delegated Domain Policies
	•	As a Data Mesh Owner, I want to delegate policy ownership to domains.
	•	Domain-driven policy namespaces.

⸻

Phase 5 — Compliance & Audit
	•	As a Compliance Officer, I want to log and audit every policy decision.
	•	Push decision logs into SIEM / audit lake → visualize with Grafana.

⸻

Phase 6 — Attribute-Based Access (ABAC)
	•	As a Security Architect, I want to combine RBAC with ABAC.
	•	Example: restrict PII access to EU analysts only.

allow {
  input.role == "data-analyst"
  input.column_sensitivity == "PII"
  input.user_region == "EU"
}


⸻

Phase 7 — Performance Optimization
	•	As a Performance Engineer, I want authorization checks to be <10ms.
	•	Enable caching / partial evaluation in the policy engine.

⸻

📊 Evolution Diagram

flowchart LR
    A[Static ACL JSON] --> B[Centralized Policy Engine]
    B --> C[GitOps-managed Policies]
    C --> D[Fine-Grained Controls]
    D --> E[Delegated Domain Ownership]
    E --> F[Audit & Compliance Logs]
    F --> G[Dynamic ABAC Rules]
    G --> H[Optimized Low-Latency Policy Engine]


⸻

🏆 End State

By the end of this journey, Trino authorization will be:
	•	✅ Centralized & consistent across clusters
	•	✅ Version-controlled with GitOps
	•	✅ Auditable & compliance-ready
	•	✅ Delegated to domain owners
	•	✅ Hybrid RBAC + ABAC for dynamic access
	•	✅ Optimized for performance

⸻

🔮 Conclusion

Moving from ACL JSON → Policy Engine is not just a technical shift.
It’s a governance transformation: giving platform teams, domain owners, and compliance officers a unified model to express, enforce, and audit access policies in a scalable way.

This journey ensures that Trino evolves from a static cluster engine into a secure, compliant, and dynamic data mesh backbone.
