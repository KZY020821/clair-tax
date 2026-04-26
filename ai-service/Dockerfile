FROM public.ecr.aws/lambda/python:3.11

WORKDIR ${LAMBDA_TASK_ROOT}

ENV APP_ENV=production
ENV EASYOCR_MODEL_DIR=/tmp/easyocr_models

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

RUN python -c "\
import os; \
os.makedirs('/tmp/easyocr_models', exist_ok=True); \
import easyocr; \
easyocr.Reader(['en'], gpu=False, model_storage_directory='/tmp/easyocr_models', download_enabled=True); \
print('EasyOCR weights downloaded successfully') \
"

COPY app/ ./app/
COPY model_artifacts/ ./model_artifacts/

CMD ["app.handlers.sqs.lambda_handler"]
