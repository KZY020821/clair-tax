## 🧠 Task: Train a Custom OCR Model for Receipt/Invoice Processing

### 1. Context & Objective

You are a senior ML engineer working on the **Clair Tax AI Service**. Your task is to train a custom OCR recognition model to improve text extraction from receipts and invoices. The existing EasyOCR base model misreads financial text (numbers, currencies, dates). You will fine-tune a model using the **"High-Quality Invoice Images for OCR"** dataset from Kaggle to enhance accuracy specifically for structured financial documents.

### 2. Mandatory Tech Stack

Follow the **exact** `deep-text-recognition-benchmark` pipeline from Clova AI (as required by EasyOCR):
- **Model Architecture**: None-VGG-BiLSTM-CTC (fully convolutional for variable-length text)[reference:0]
- **Framework**: PyTorch
- **Training Script**: Use the official modified version from `JaidedAI/EasyOCR/trainer`[reference:1]

### 3. Step-by-Step Execution Plan

#### **Phase 0: Environment Setup**
Create a Python 3.9+ virtual environment and install:
```bash
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu118  # or cpu version
pip install kagglehub easyocr opencv-python nltk jiwer editdistance Pillow scikit-learn
```

Set up your Kaggle API credentials:
```python
import kagglehub
path = kagglehub.dataset_download("osamahosamabdellatif/high-quality-invoice-images-for-ocr")
dataset_path = Path(path)
```

#### **Phase 1: Dataset Exploration & Validation**
1. **Inspect the dataset structure** recursively under the downloaded directory:
   - Count images (.jpg, .png)
   - Locate any annotation files (.json, .xml, .csv)
   - Print 3 example filenames with their first 5 lines (if text files) or print image dimensions

2. **Validate data quality**:
   - Check if annotations exist in COCO, JSON Lines, or custom key-value format
   - If annotations are missing, notify the user immediately and offer to:
     - Use an LLM (GPT-4o-mini) to generate synthetic annotations
     - Proceed with synthetic data generation using `TextRecognitionDataGenerator`[reference:2]

3. **Log the findings**: Save a `data_report.md` with counts, formats, and any issues.

#### **Phase 2: Data Preprocessing for Training**
The training pipeline expects a **specific folder structure** for `deep-text-recognition-benchmark`:

```
data/
  └── ocr_data/
      ├── train/
      │   ├── image_001.jpg
      │   ├── image_001.txt   # Contains the ground truth text (one line)
      │   ├── image_002.jpg
      │   └── image_002.txt
      └── test/ (or val/)
          └── ... (same structure)
```

**Conversion script requirements** (write a single Python script `prepare_dataset.py`):
- For each image, the corresponding `.txt` file must contain **only the plain text** of all words in reading order (space-separated).
- If bounding box annotations exist, extract text in the correct horizontal order (left-to-right, top-to-bottom).
- Resize all images to a uniform height (32 or 64 pixels) while preserving aspect ratio via padding.
- Split data: 80% train, 20% test (use deterministic random seed 42).

If no annotations exist, generate a synthetic dataset with 50,000 receipts using `TextRecognitionDataGenerator`:
  ```python
  # Example generation command
  !python Trdg/generator.py --output_dir data/ocr_data/train \
      --number 50000 --language en \
      --text_format csv --dict texts/receipt_dict.txt
  ```

#### **Phase 3: Training Pipeline Integration**

1. **Clone the official trainer repository**:
   ```bash
   git clone https://github.com/JaidedAI/EasyOCR.git
   cp -r EasyOCR/trainer ./ocr_trainer
   cd ocr_trainer
   ```

2. **Configure `train.py` parameters** (edit or create a config file):
   ```yaml
   # config/train_receipt.yaml
   character: '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz$%.,#-:;'
   num_class: 80  # adjust based on character set
   imgH: 32
   imgW: 100
   batch_size: 64
   epochs: 50
   lr: 0.001
   workers: 4
   train_data: './data/ocr_data/train'
   valid_data: './data/ocr_data/test'
   save_interval: 5
   ```

3. **Launch training** with multi-GPU support if available:
   ```bash
   python train.py --config config/train_receipt.yaml --cuda
   ```
   - Capture training logs (loss, accuracy, character error rate) every 100 batches.
   - Save the best checkpoint based on validation loss.

#### **Phase 4: Model Export for EasyOCR**

Training produces a `.pth` file. To use it with EasyOCR, you need **three files with the same base name**:
- `custom_receipt.pth` (the trained weights)
- `custom_receipt.yaml` (model configuration)[reference:3]
- `custom_receipt.py` (network definition)[reference:4]

**Generate the missing files** from the training experiment:
   - Extract the model architecture and hyperparameters from the training logs.
   - Use the `CustomModel` class from `custom_example.py` as a base[reference:5].
   - Override the `__init__` and `forward` methods to match your trained network.

**Deploy locally for verification**:
   ```python
   import easyocr
   reader = easyocr.Reader(['en'], recog_network='custom_receipt', 
                           model_storage_directory='~/.EasyOCR/model',
                           user_network_directory='~/.EasyOCR/user_network')
   result = reader.readtext('sample_invoice.jpg', paragraph=True)
   print(result)  # Verify numeric & date accuracy
   ```

### 4. Quality Gates (Must Pass)

- **Character Error Rate (CER)** on the test set < 5% for digits, currency symbols, and dates.
- **Custom model must be callable** via EasyOCR with zero additional code changes.
- **Training must complete** within 8 hours on a single T4 GPU (or 24 hours on CPU).
- **All artifacts**: Save the final `.pth`, `.yaml`, `.py` files, plus `training_metrics.json` and `config.yaml`.

### 5. Failure Handling & Fallbacks

- If the Kaggle dataset is insufficient (e.g., no text annotations), **automatically fall back to generating synthetic receipts** using `TextRecognitionDataGenerator`[reference:6].
- If the `deep-text-recognition-benchmark` pipeline fails, implement a fallback using **Pytorch Lightning** + **OCR-Evaluation** framework.
- If validation CER remains > 20% after 10 epochs, apply data augmentation (rotation, blur, noise) or increase image height to 64 px.

### 6. Output Requirements

Deliver the following in your final response:

1. **The complete `prepare_dataset.py` script** for dataset preprocessing.
2. **The final `config.yaml`** used for training.
3. **Console logs** of the final epoch: loss, CER, and accuracy.
4. **A short summary** (3-5 sentences) explaining any deviations from the plan and the final model's readiness for production.

### 7. Success Definition

The AI Service maintainer (the user) should be able to run `easyocr.Reader(['en'], recog_network='custom_receipt')` and see improved extraction of dates, totals, and amounts compared to the base model. Historical OCR errors on financial numeric patterns must be reduced by at least 30%.

## ⚠️ Critical Reminders

- **Do not assume the dataset has annotations** – always validate programmatically and handle missing labels.
- **Use absolute paths** when constructing the data loader to avoid relative-path bugs.
- **Save all hyperparameters** in source control — reproducibility is mandatory[reference:7].
- **Set deterministic seeds** (torch, numpy, random) for the entire pipeline.
- **Log every major step** with timestamps and resource usage (CPU/GPU memory).

Proceed with **Phase 0** now. Provide status updates after each phase. If any step is ambiguous, ask for clarification before proceeding.