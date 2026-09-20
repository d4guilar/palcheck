# Costa Rica Glossary (orientation only)

Status: **UNVERIFIED.** Entries are *orientation*, drawn from the names in the brief and general familiarity, so an international reader knows what kind of thing each term is.
They are **not** statements of law. Nothing here has been checked against an authoritative source. Do not build behavior on this page.

Layer key: **D** = domain concept (what kind of thing) · **L** = legal rule (what law requires) · **S** = software behavior (what PalCheck does).
This glossary writes only layer **D**, tentatively. Layers **L** and **S** are `RESEARCH REQUIRED` / `NOT IMPLEMENTED` for every entry.

Format: **Native term**: English explanation. The English gloss is a label to aid reading; it must never replace the native term where meaning could be lost.

| Native term | English explanation (orientation, UNVERIFIED) | Category | Likely global primitive (hypothesis) | Research | Implementation |
|---|---|---|---|---|---|
| **Aguinaldo** | Mandatory annual bonus. The brief states it must not be treated as a discretionary performance bonus. | Compensation | Compensation element | `UNRESEARCHED` | `NOT IMPLEMENTED` |
| **Vacaciones** | Vacation / paid time away from work. Whether it maps to a generic "PTO" concept is exactly what must be researched. | Leave | Leave | `UNRESEARCHED` | `NOT IMPLEMENTED` |
| **Incapacidad / Incapacidades** | Work incapacity for health reasons (sick-related leave). Implies health-related, potentially sensitive data. | Leave | Leave | `UNRESEARCHED` | `NOT IMPLEMENTED` |
| **Jornada laboral / Jornadas laborales** | Working day / working-time arrangement(s). | WorkingTime | Work schedule | `UNRESEARCHED` | `NOT IMPLEMENTED` |
| **Horas extra** | Overtime (hours beyond the regular working time). | WorkingTime, Compensation | Time and pay element | `UNRESEARCHED` | `NOT IMPLEMENTED` |
| **CCSS** | Costa Rica's social security institution (Caja Costarricense de Seguro Social). Relevant to employer and employee obligations. Whether the obligations sit with one institution or several is RESEARCH REQUIRED. | SocialSecurity | Statutory social-security enrolment / contribution | `UNRESEARCHED` | `NOT IMPLEMENTED` |
| **Deducciones** | Payroll deductions. Which are statutory vs. voluntary is RESEARCH REQUIRED. | TaxAndDeductions, PayrollHandoff | Payroll deduction | `UNRESEARCHED` | `NOT IMPLEMENTED` |
| **Preaviso** | Notice (in the context of ending employment). | Termination | Termination (notice) | `UNRESEARCHED` | `NOT IMPLEMENTED` |
| **Cesantía** | A termination-related concept (commonly rendered as severance). The English gloss is *not* to be trusted as equivalent. | Termination | Termination (separation payment) | `UNRESEARCHED` | `NOT IMPLEMENTED` |
| **Liquidación / Liquidaciones** | Final settlement at the end of employment. What it comprises is RESEARCH REQUIRED. | Termination, PayrollHandoff | Termination (final settlement) | `UNRESEARCHED` | `NOT IMPLEMENTED` |
| **Feriados** | Public holidays. | Calendar | Holiday calendar | `UNRESEARCHED` | `NOT IMPLEMENTED` |
| **Tipos de contrato** | Contract types (kinds of employment contract). Drives the Employment worker type and duration model. | Contracts, WorkerClassification | Employment (worker type / contract) | `UNRESEARCHED` | `NOT IMPLEMENTED` |
| **Colón costarricense (CRC)** | Costa Rica's currency; ISO code CRC. Whether compensation may be denominated in other currencies is RESEARCH REQUIRED. | Compensation | `Money` (amount, currency) | `UNRESEARCHED` | `NOT IMPLEMENTED` |
| **Patrono / estructura del empleador** | Employer, and how employers are structured and registered. Drives the Legal Entity model. Spanish terms to be confirmed. | EmployerStructure | Legal Entity | `UNRESEARCHED` | `NOT IMPLEMENTED` |
| **Planilla** | Payroll (the payroll run or payroll register). Spanish usage to be confirmed. | PayrollHandoff | Payroll event / payroll handoff | `UNRESEARCHED` | `NOT IMPLEMENTED` |

## Translation cautions (from the brief and design principles)

- **Do not** render Aguinaldo as "bonus" or "13th month" in the product. Use the native term with the explanation above until research says otherwise.
- **Do not** assume Cesantía equals a generic English "severance". The mapping to a global termination primitive is `UNKNOWN` and must be recorded as such.
- **Do not** assume Vacaciones behaves like a generic PTO accrual. Nothing about accrual is known here, and nothing may be inferred.
- **Do not** assume the same words carry the same meaning in another Latin American country. Vocabulary is per country ([LATAM_STRATEGY.md](../LATAM_STRATEGY.md) section 7).

## Not yet on this list

Concepts that may matter but were not named in the brief are added through the backlog ([RESEARCH_BACKLOG.md](RESEARCH_BACKLOG.md)), not from memory. This list is
deliberately not a survey of Costa Rican labor law.

*Not legal advice.*
