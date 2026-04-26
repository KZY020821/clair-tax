from pydantic import BaseModel


class LabeledBlock(BaseModel):
    text: str
    confidence: float
    bbox: list[list[int]]
    line_index: int
    is_amount: bool = False
    is_date: bool = False
    is_merchant: bool = False


class AnnotatedReceipt(BaseModel):
    receipt_id: str
    s3_key: str | None = None
    image_path: str | None = None
    ground_truth_amount: str | None = None
    ground_truth_date: str | None = None
    ground_truth_merchant: str | None = None
    blocks: list[LabeledBlock] = []
