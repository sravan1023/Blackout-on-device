package com.example.blackout.engine

enum class RedactionMode(val label: String, val description: String, val sampleText: String) {
    HIPAA(
        label = "Medical / HIPAA",
        description = "Redacts all 18 PHI identifiers including relational references",
        sampleText = "Patient Jane Smith, DOB 04/12/1978, MRN 847-293-1, was admitted on 03/15/2024 at St. Mary's Hospital in Chicago, IL. Her SSN is 123-45-6789. Attending physician Dr. Michael Torres can be reached at (312) 555-0148.",
    ),
    TACTICAL(
        label = "Tactical / First Responder",
        description = "Redacts victims & witnesses, keeps suspect/vehicle info",
        sampleText = "Victim Sarah Johnson, 34F, was assaulted at 1429 Oak Street at 22:15. Suspect is a Hispanic male, approx. 6'1\", driving a black Toyota Camry, plate ABC-1234. Witness Marcus Webb, (555) 304-2211, was present at the scene.",
    ),
    JOURNALISM(
        label = "Journalism",
        description = "Protects source identity and meeting locations",
        sampleText = "SOURCE_A (phone: 202-555-0199) confirmed the story at the coffee shop on 5th Ave last Tuesday. They requested full anonymity due to fear of retaliation. My inside contact at the agency, referred to as \"Falcon,\" provided the documents.",
    ),
    FIELD_SERVICE(
        label = "Field Service",
        description = "Redacts customer PII and security credentials",
        sampleText = "Service call for Robert Chen at 4521 Maple Drive, Austin TX 78701. Gate code: 4892*. Wi-Fi SSID: HomeNet_Chen, password: FlowerPot#7. Customer contact: (512) 555-7823. Account #AC-78234-TX.",
    ),
    FINANCIAL(
        label = "Financial / Legal",
        description = "Redacts SSNs, account numbers, and transaction IDs",
        sampleText = "Wire transfer for James Miller (SSN: 987-65-4321) from Chase checking account #4521-8834-2291-0042, routing 021000021, to brokerage account #BR-7712-US. Amount: \$24,500. TXN ID: TF-20240315-9918273.",
    ),
}
