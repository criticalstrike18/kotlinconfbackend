FROM python:3.9-slim

WORKDIR /app

# Copy requirements first to leverage Docker cache
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy the script and storage directory with database files
COPY main.py .
COPY storage/ ./storage/

# Set environment variables
ENV STORAGE_DIR=/app/storage
ENV PG_HOST=db
ENV PG_PORT=5432
ENV PG_DBNAME=kotlinconfg
ENV PG_USER=postgres
ENV PG_PASSWORD=postgres
ENV BACKEND_URL=http://backend:8080

# Run the script
CMD ["python", "main.py"]