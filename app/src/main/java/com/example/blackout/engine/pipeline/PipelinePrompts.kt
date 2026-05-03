package com.example.blackout.engine.pipeline

object PipelinePrompts {

    fun classifyPrompt(text: String): String = """
You are a document classifier for a privacy redaction system.

Analyze the following text and determine:
1. DOCUMENT TYPE — what kind of document is this?
2. REDACTION CATEGORY — which privacy category applies?

Available categories:
- Medical: patient records, clinical notes, lab results, prescriptions, health information
- Financial: bank statements, tax returns, W-2 forms, investment statements, financial accounts
- Legal: contracts, leases, purchase agreements, legal correspondence
- Tactical: police reports, incident reports, first responder notes
- Journalism: source notes, interview transcripts, investigative notes
- Field Service: service orders, work orders, customer site notes
- General: anything not fitting the above

Respond in EXACTLY this format (two lines only, no other text):
DOCUMENT_TYPE: <type>
CATEGORY: <category>

INPUT:
$text
    """.trimIndent()

    fun detectPromptFor(category: String, text: String): String = when (category.lowercase()) {
        "medical" -> medicalDetectPrompt(text)
        "financial" -> financialDetectPrompt(text)
        "legal" -> legalDetectPrompt(text)
        "tactical" -> tacticalDetectPrompt(text)
        "journalism" -> journalismDetectPrompt(text)
        "field service" -> fieldServiceDetectPrompt(text)
        else -> generalDetectPrompt(text)
    }

    fun redactPrompt(text: String, detections: List<Detection>): String {
        val detectionList = detections.joinToString("\n") { "- ${it.category}: ${it.originalText}" }
        return """
You are a text redaction engine. Your ONLY job is to replace sensitive
information with placeholders.

SENSITIVE ITEMS FOUND (replace ALL of these and anything similar):
$detectionList

PLACEHOLDER FORMAT:
- Names → [NAME_1], [NAME_2], etc.
- Dates → [DATE_1], [DATE_2], etc.
- Phone numbers → [PHONE_1], [PHONE_2], etc.
- Emails → [EMAIL_1], [EMAIL_2], etc.
- SSNs → [SSN_1], etc.
- Locations/addresses → [LOCATION_1], [LOCATION_2], etc.
- Medical record numbers → [MRN_1], etc.
- Account numbers → [ACCOUNT_1], etc.
- Victims → [VICTIM_1], etc.
- Witnesses → [WITNESS_1], etc.
- Sources → [SOURCE_1], etc.
- Customers → [CUSTOMER_1], etc.
- Security credentials → [SECURE_1], etc.
- Any other ID → [ID_1], etc.

RULES:
1. Replace EVERY name, date, phone, email, address, and ID number
   in the text — even if not listed above
2. Keep all non-sensitive text EXACTLY as it appears
3. Do NOT fix spelling, grammar, or formatting
4. Output ONLY the redacted text — no explanations

ORIGINAL TEXT:
$text

REDACTED TEXT:
        """.trimIndent()
    }

    fun validatePromptFor(category: String, redactedText: String): String = when (category.lowercase()) {
        "medical" -> medicalValidatePrompt(redactedText)
        "financial" -> financialValidatePrompt(redactedText)
        "legal" -> legalValidatePrompt(redactedText)
        "tactical" -> tacticalValidatePrompt(redactedText)
        "journalism" -> journalismValidatePrompt(redactedText)
        "field service" -> fieldServiceValidatePrompt(redactedText)
        else -> generalValidatePrompt(redactedText)
    }

    // ═══════════════════════════════════════════
    // MEDICAL
    // ═══════════════════════════════════════════

    private fun medicalDetectPrompt(text: String): String = """
You are a PHI/PII detection engine for medical documents.

Find every instance of protected health information in the text below.

IDENTIFIERS TO DETECT (list each one found):
- NAME: patient names, family member names, provider/doctor names, any person referenced
- DATE: specific dates with month/day (year alone is OK to keep)
- PHONE: telephone numbers
- FAX: fax numbers
- EMAIL: email addresses
- SSN: Social Security Numbers
- MRN: medical record numbers
- INSURANCE: health plan or insurance ID numbers
- ACCOUNT: bank or billing account numbers
- LICENSE: certificate or license numbers
- LOCATION: street addresses, city names, ZIP codes, hospital names with city (state name alone is OK)
- VEHICLE: vehicle identifiers or license plates
- DEVICE: medical device identifiers or serial numbers
- URL: web URLs
- IP: IP addresses
- ID: any other unique identifying number

IMPORTANT — DO NOT detect these (they must be PRESERVED):
- Diagnoses and conditions (e.g., Type 2 diabetes, CHF, depression)
- Medications and dosages (e.g., Metformin 500mg BID, Lisinopril 10mg)
- Vital signs (e.g., BP 140/90, HR 82, O2 94%, temp 98.6)
- Body locations (e.g., left heel, right lower quadrant, chest)
- Lab values, units, reference ranges, abnormal flags
- Clinical procedures and treatment instructions
- Ages under 90 (age 85 is OK; age 92 becomes ">=90")
- Year alone (2024 is OK; January 5, 2024 → detect as DATE)
- State names (California is OK; 123 Main St, Sacramento → detect as LOCATION)
- Provider specialties (Cardiologist is OK; Dr. Smith → detect as NAME)

SPECIAL RULES:
- Family members mentioned by name ARE identifiers: "patient's daughter Lisa" → NAME: Lisa
- Relational references that reveal identity: "her husband" with a name → detect the name
- Provider names ARE identifiers even though their specialty is preserved

For each identifier found, output EXACTLY one line per item:
CATEGORY: exact text as it appears in the input

CRITICAL: Copy the text EXACTLY character-for-character from the input.
Do NOT correct spelling, fix OCR errors, or reformat.
Do NOT output template placeholders like [YOUR NAME].

OUTPUT ONLY detection lines. No explanations, no headers, no numbering.

INPUT:
$text
    """.trimIndent()

    private fun medicalValidatePrompt(redactedText: String): String = """
You are a privacy compliance auditor for medical documents.

The text below has been redacted. Items in square brackets like
[NAME_1], [DATE_2], [EMAIL_1], [PHONE_1], [LOCATION_1] are
CORRECTLY REDACTED placeholders — they are NOT leaks.

Look for REAL personal information that is still visible as plain
text (not inside square brackets). Examples of real leaks:
- A person's name like "Jane Smith" (not [NAME_1])
- A phone number like "555-1234" (not [PHONE_1])
- An email like "jane@mail.com" (not [EMAIL_1])

DO NOT report anything inside square brackets — those are correct.
DO NOT report medical terms, medications, diagnoses, or vitals.
DO NOT report years alone, state names alone, or ages under 90.

Your response MUST start with exactly one of:
RESULT: PASS
or
RESULT: FAIL

If FAIL, list each REAL leaked item (plain text, not placeholders):
CATEGORY: exact leaked text

REDACTED TEXT:
$redactedText
    """.trimIndent()

    // ═══════════════════════════════════════════
    // FINANCIAL
    // ═══════════════════════════════════════════

    private fun financialDetectPrompt(text: String): String = """
You are a PII detection engine for financial documents.

Find every instance of personally identifiable information in the text below.

IDENTIFIERS TO DETECT:
- NAME: full legal names of account holders, signers, beneficiaries
- SSN: Social Security Numbers (full or partial with dashes)
- DOB: dates of birth
- ADDRESS: home addresses, mailing addresses (PO Boxes included)
- PHONE: personal phone numbers (NOT toll-free 1-800/1-888 customer service numbers)
- EMAIL: personal email addresses (NOT generic support@company.com)
- ACCOUNT: bank account numbers, checking/savings account numbers
- ROUTING: bank routing numbers
- CARD: credit/debit card numbers
- TAXID: employer EIN, tax identification numbers
- TXN: transaction IDs, confirmation numbers
- BROKERAGE: brokerage or investment account numbers
- LICENSE: driver's license numbers
- ID: any other unique identifying number

DO NOT detect (PRESERVE these):
- Dollar amounts and financial figures
- Institution names (Chase, Wells Fargo, IRS)
- Toll-free customer service numbers (1-800-xxx-xxxx, 1-888-xxx-xxxx)
- Generic company email addresses (support@, info@, service@)
- Account types (checking, savings, brokerage)
- Interest rates, APR, fee amounts
- Tax form types and box labels (W-2 Box 1, 1040 Line 7)
- Tax years
- General financial terms and legal language

For each identifier found, output EXACTLY one line:
CATEGORY: exact text as it appears in the input

CRITICAL: Copy the text EXACTLY. Do NOT correct OCR errors.

OUTPUT ONLY detection lines. No explanations.

INPUT:
$text
    """.trimIndent()

    private fun financialValidatePrompt(redactedText: String): String = """
You are a privacy compliance auditor for financial documents.

The text below has been redacted. Items in square brackets like
[NAME_1], [SSN_2], [ACCOUNT_1] are CORRECTLY REDACTED — NOT leaks.

Look for REAL personal information still visible as plain text:
- Person names, SSNs, bank account/routing/card numbers
- Home or mailing addresses, personal phone/email
- Tax IDs, driver's license numbers

DO NOT report items in square brackets — those are correct.
DO NOT report dollar amounts, institution names, toll-free numbers,
tax form labels, or general financial terms.

Your response MUST start with exactly one of:
RESULT: PASS
or
RESULT: FAIL

If FAIL, list each REAL leaked item:
CATEGORY: exact leaked text

REDACTED TEXT:
$redactedText
    """.trimIndent()

    // ═══════════════════════════════════════════
    // LEGAL
    // ═══════════════════════════════════════════

    private fun legalDetectPrompt(text: String): String = """
You are a PII detection engine for legal documents (contracts, leases,
purchase agreements, legal correspondence).

Find every instance of personally identifiable information.

IDENTIFIERS TO DETECT:
- NAME: buyer names, seller names, tenant names, landlord names, attorney names, agent names, signer names
- ADDRESS: home addresses, property addresses for identification (but see PRESERVE for property specs)
- PHONE: personal phone numbers
- EMAIL: personal email addresses
- SSN: Social Security Numbers
- ACCOUNT: bank account numbers, escrow account numbers
- LICENSE: driver's license numbers, professional license numbers
- DATE: specific dates of birth, specific signing dates with day precision
- ID: notary commission numbers, case numbers, deed numbers, parcel numbers

DO NOT detect (PRESERVE these):
- Property specifications (bedrooms, bathrooms, sq ft, lot size, year built)
- Legal terms, clauses, and standard contract language
- Zoning information and land use designations
- Dollar amounts (purchase price, rent, deposits, fees)
- Interest rates, loan terms, payment schedules
- Standard contingencies (inspection, financing, appraisal)
- Closing timeline and possession dates
- General location descriptions (city, county, state)
- Company/firm names and business entities

For each identifier found, output EXACTLY one line:
CATEGORY: exact text as it appears in the input

CRITICAL: Copy the text EXACTLY. Do NOT correct OCR errors.

OUTPUT ONLY detection lines. No explanations.

INPUT:
$text
    """.trimIndent()

    private fun legalValidatePrompt(redactedText: String): String = """
You are a privacy compliance auditor for legal documents.

The text below has been redacted. Items in square brackets like
[NAME_1], [ADDRESS_1] are CORRECTLY REDACTED — NOT leaks.

Look for REAL personal information still visible as plain text:
- Person names (buyers, sellers, tenants, landlords, attorneys)
- SSNs, account numbers, license numbers
- Personal phone numbers or emails
- Home addresses used for identification

DO NOT report items in square brackets — those are correct.
DO NOT report property specs, dollar amounts, legal terms,
company names, or general location descriptions.

Your response MUST start with exactly one of:
RESULT: PASS
or
RESULT: FAIL

If FAIL, list each REAL leaked item:
CATEGORY: exact leaked text

REDACTED TEXT:
$redactedText
    """.trimIndent()

    // ═══════════════════════════════════════════
    // TACTICAL
    // ═══════════════════════════════════════════

    private fun tacticalDetectPrompt(text: String): String = """
You are a PII detection engine for law enforcement and first responder
documents (police reports, incident reports, field notes).

Find personally identifiable information for VICTIMS, WITNESSES, and MINORS.

IDENTIFIERS TO DETECT:
- VICTIM: victim names, victim descriptions that could identify them
- WITNESS: witness names
- MINOR: names of any person under 18
- ADDRESS: home addresses of victims, witnesses, minors
- CONTACT: personal phone numbers and emails of victims/witnesses
- DATE: dates of birth of victims/witnesses

DO NOT detect (PRESERVE these — critical for investigations):
- Suspect names and physical descriptions (height, weight, race, clothing)
- Suspect vehicle information (make, model, color, license plate)
- Officer names and badge numbers
- Crime details (type of crime, weapon used, method)
- Location of the incident (crime scene address)
- Case numbers and report numbers
- Times and dates of the incident itself
- Evidence descriptions

SPECIAL RULES:
- Suspect info is NEVER redacted — it must remain for identification
- Officer/responder names are NEVER redacted
- Victim home addresses ARE redacted but crime scene locations are NOT
- If a minor is mentioned, their name AND age are redacted

For each identifier found, output EXACTLY one line:
CATEGORY: exact text as it appears in the input

CRITICAL: Copy the text EXACTLY. Do NOT correct OCR errors.

OUTPUT ONLY detection lines. No explanations.

INPUT:
$text
    """.trimIndent()

    private fun tacticalValidatePrompt(redactedText: String): String = """
You are a privacy compliance auditor for law enforcement documents.

The text below has been redacted. Items in square brackets like
[VICTIM_1], [WITNESS_1], [ADDRESS_1] are CORRECTLY REDACTED — NOT leaks.

Look for REAL personal information of victims, witnesses, or minors
still visible as plain text:
- Victim or witness names
- Minor names
- Home addresses of victims/witnesses
- Personal phone/email of victims/witnesses

DO NOT report items in square brackets — those are correct.
DO NOT report suspect descriptions, vehicle info, officer names,
crime scene locations, case numbers, or incident details.

Your response MUST start with exactly one of:
RESULT: PASS
or
RESULT: FAIL

If FAIL, list each REAL leaked item:
CATEGORY: exact leaked text

REDACTED TEXT:
$redactedText
    """.trimIndent()

    // ═══════════════════════════════════════════
    // JOURNALISM
    // ═══════════════════════════════════════════

    private fun journalismDetectPrompt(text: String): String = """
You are a PII detection engine for journalism and investigative
documents (source notes, interview transcripts, investigative notes).

Find information that could identify confidential sources.

IDENTIFIERS TO DETECT:
- SOURCE: source names, aliases, codenames, any identifying reference to a source
- LOCATION: meeting locations, source home/work addresses, specific venues
- CONTACT: source phone numbers, email addresses, social media handles
- ID: badge numbers, employee IDs, login credentials, any unique ID that could identify a source
- DATE: specific meeting dates/times that could narrow down a source

DO NOT detect (PRESERVE these):
- Public official names (politicians, CEOs, spokespersons)
- Institution and organization names
- Published or publicly known facts
- General geographic regions (city/state level)
- Policy details, legislation references
- Reporter/journalist names (they are public)
- Story details and findings

SPECIAL RULES:
- Codenames and aliases ARE identifiers if they could be traced back
- "My contact at [organization]" — redact any identifying details
- Physical descriptions of sources ARE identifiers
- Source relationship descriptions ("the intern", "the manager on 3rd floor") can identify — redact

For each identifier found, output EXACTLY one line:
CATEGORY: exact text as it appears in the input

CRITICAL: Copy the text EXACTLY. Do NOT correct OCR errors.

OUTPUT ONLY detection lines. No explanations.

INPUT:
$text
    """.trimIndent()

    private fun journalismValidatePrompt(redactedText: String): String = """
You are a privacy compliance auditor for journalism documents.

The text below has been redacted. Items in square brackets like
[SOURCE_1], [LOCATION_1], [CONTACT_1] are CORRECTLY REDACTED — NOT leaks.

Look for REAL information that could identify confidential sources:
- Source names or aliases
- Meeting locations or source addresses
- Source phone numbers, emails, social media
- Identifying descriptions or unique IDs

DO NOT report items in square brackets — those are correct.
DO NOT report public official names, institution names,
reporter names, published facts, or general regions.

Your response MUST start with exactly one of:
RESULT: PASS
or
RESULT: FAIL

If FAIL, list each REAL leaked item:
CATEGORY: exact leaked text

REDACTED TEXT:
$redactedText
    """.trimIndent()

    // ═══════════════════════════════════════════
    // FIELD SERVICE
    // ═══════════════════════════════════════════

    private fun fieldServiceDetectPrompt(text: String): String = """
You are a PII detection engine for field service documents (work orders,
service calls, customer site notes, technician reports).

Find customer personal information and security credentials.

IDENTIFIERS TO DETECT:
- CUSTOMER: customer names, tenant names, homeowner names
- ADDRESS: customer home/business addresses, service addresses
- CONTACT: customer phone numbers, personal email addresses
- SECURE: gate codes, alarm codes, lockbox codes, Wi-Fi passwords, access PINs, security system details
- ACCOUNT: customer account numbers, service agreement numbers
- DATE: customer dates of birth
- EMAIL: customer personal emails
- ID: customer ID numbers, membership numbers

DO NOT detect (PRESERVE these):
- Equipment make, model, and serial numbers
- Fault codes, error codes, diagnostic readings
- Parts numbers and replacement part names
- Service instructions and repair procedures
- Warranty information and service terms
- Company/technician names (they are service providers, not customers)
- General service descriptions and work performed
- Equipment specifications and settings

SPECIAL RULES:
- Wi-Fi SSID names that contain the customer's name ARE identifiers
- Gate codes and alarm codes are ALWAYS identifiers
- Technician names are NOT identifiers (they are providers)
- Equipment serial numbers are NOT identifiers (they identify devices, not people)

For each identifier found, output EXACTLY one line:
CATEGORY: exact text as it appears in the input

CRITICAL: Copy the text EXACTLY. Do NOT correct OCR errors.

OUTPUT ONLY detection lines. No explanations.

INPUT:
$text
    """.trimIndent()

    private fun fieldServiceValidatePrompt(redactedText: String): String = """
You are a privacy compliance auditor for field service documents.

The text below has been redacted. Items in square brackets like
[CUSTOMER_1], [ADDRESS_1], [SECURE_1] are CORRECTLY REDACTED — NOT leaks.

Look for REAL customer information or security credentials:
- Customer names
- Customer addresses, phone numbers, emails
- Gate codes, alarm codes, Wi-Fi passwords, access PINs
- Account numbers or customer IDs

DO NOT report items in square brackets — those are correct.
DO NOT report equipment details, fault codes, parts numbers,
service instructions, or technician names.

Your response MUST start with exactly one of:
RESULT: PASS
or
RESULT: FAIL

If FAIL, list each REAL leaked item:
CATEGORY: exact leaked text

REDACTED TEXT:
$redactedText
    """.trimIndent()

    // ═══════════════════════════════════════════
    // GENERAL (fallback)
    // ═══════════════════════════════════════════

    private fun generalDetectPrompt(text: String): String = """
You are a PII detection engine.

Find every instance of personally identifiable information in the text below.

IDENTIFIERS TO DETECT:
- NAME: person names
- DATE: specific dates (birth dates, signing dates)
- PHONE: phone numbers
- EMAIL: email addresses
- SSN: Social Security Numbers
- ADDRESS: physical addresses
- ACCOUNT: account numbers
- ID: any other unique identifying number or code

For each identifier found, output EXACTLY one line:
CATEGORY: exact text as it appears in the input

CRITICAL: Copy the text EXACTLY. Do NOT correct OCR errors.

OUTPUT ONLY detection lines. No explanations.

INPUT:
$text
    """.trimIndent()

    private fun generalValidatePrompt(redactedText: String): String = """
You are a privacy compliance auditor.

The text below has been redacted. Items in square brackets like
[NAME_1] are CORRECTLY REDACTED — NOT leaks.

Look for REAL personal information still visible as plain text:
person names, dates, phone numbers, email addresses, SSNs,
addresses, account numbers, or other identifying information.

DO NOT report items in square brackets — those are correct.

Your response MUST start with exactly one of:
RESULT: PASS
or
RESULT: FAIL

If FAIL, list each REAL leaked item:
CATEGORY: exact leaked text

REDACTED TEXT:
$redactedText
    """.trimIndent()
}
