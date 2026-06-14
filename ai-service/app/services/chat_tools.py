CHAT_TOOLS: list[dict] = [
    {
        "type": "function",
        "function": {
            "name": "get_relief_categories",
            "description": (
                "Get all available Malaysian income tax relief categories for a given "
                "Year of Assessment. Use this to answer questions about what reliefs exist, "
                "their caps, and eligibility."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "year": {"type": "integer", "description": "Year of Assessment e.g. 2025"}
                },
                "required": ["year"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_user_year_summary",
            "description": (
                "Get the user's tax workspace summary for a given Year of Assessment, "
                "including how much they have claimed per relief category."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "year": {"type": "integer", "description": "Year of Assessment"}
                },
                "required": ["year"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "update_profile",
            "description": (
                "Update the user's tax profile (marital status, disability status, dependents). "
                "ALWAYS ask the user to confirm before calling this tool. "
                "Use only when the user explicitly states their personal status."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "marital_status": {
                        "type": "string",
                        "enum": ["single", "married", "previously_married"],
                        "description": "Marital status of the user",
                    },
                    "is_disabled": {
                        "type": "boolean",
                        "description": "Whether the user has a disability",
                    },
                    "spouse_disabled": {
                        "type": "boolean",
                        "description": "Whether the user's spouse has a disability",
                    },
                    "spouse_working": {
                        "type": "boolean",
                        "description": "Whether the user's spouse is working",
                    },
                    "has_children": {
                        "type": "boolean",
                        "description": "Whether the user has children",
                    },
                },
                "required": ["marital_status", "is_disabled"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "assign_receipt_to_year",
            "description": (
                "Assign an existing receipt (by UUID) to a Year of Assessment workspace "
                "and optionally set its relief category. "
                "ALWAYS ask the user to confirm before calling this tool."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "receipt_id": {
                        "type": "string",
                        "description": "UUID of the existing receipt to assign",
                    },
                    "year": {
                        "type": "integer",
                        "description": "Year of Assessment to assign the receipt to",
                    },
                    "relief_category_id": {
                        "type": "string",
                        "description": "Optional UUID of the relief category for this receipt",
                    },
                },
                "required": ["receipt_id", "year"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "process_receipt_attachment",
            "description": (
                "Process a receipt image or PDF that the user has attached to the chat. "
                "Runs OCR extraction, creates a receipt record, and adds it to the specified "
                "Year of Assessment workspace under the matching relief category. "
                "Use when the user uploads a receipt and asks to add it to a specific year or category. "
                "ALWAYS ask the user to confirm before calling this tool."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "year": {
                        "type": "integer",
                        "description": "Year of Assessment (e.g. 2024)",
                    },
                    "relief_category_hint": {
                        "type": "string",
                        "description": (
                            "Name or keyword for the relief category "
                            "(e.g. 'medical', 'education', 'lifestyle'). Optional."
                        ),
                    },
                    "attachment_s3_key": {
                        "type": "string",
                        "description": "S3 key of the chat attachment. Auto-injected — omit this field.",
                    },
                },
                "required": ["year"],
            },
        },
    },
]

# Tools that mutate state — require user confirmation before execution
WRITE_TOOLS: frozenset[str] = frozenset({"update_profile", "assign_receipt_to_year", "process_receipt_attachment"})

# Tools that are read-only — safe to execute immediately
READ_TOOLS: frozenset[str] = frozenset({"get_relief_categories", "get_user_year_summary"})
