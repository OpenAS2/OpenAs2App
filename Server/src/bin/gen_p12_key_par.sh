#!/bin/bash

x=$(basename "$0")

if [ $# -ne 1 ]; then
  echo "Usage: ${x} <response file>"
  exit 1
fi

RESPONSE_FILE=$1
LOG_DIR="generated_certs/logs"
mkdir -p "$LOG_DIR"
LOG_FILE="${LOG_DIR}/generate_certs_$(date +"%Y%m%d_%H%M%S").log"

exec > >(tee -a "$LOG_FILE") 2>&1  # Redirect output and errors to log

echo "========= Certificate Generation Started at $(date) =========" | tee -a "$LOG_FILE"

if [ ! -f "$RESPONSE_FILE" ]; then
  echo "Error: Response file '$RESPONSE_FILE' not found!" | tee -a "$LOG_FILE"
  exit 1
fi

# Ensure JAVA_HOME is set
if [ -z "$JAVA_HOME" ]; then
  baseDir=$(dirname "$0")
  . "${baseDir}/find_java"
fi

if [ -z "$JAVA_HOME" ]; then
  echo "ERROR: Cannot find JAVA_HOME" | tee -a "$LOG_FILE"
  exit 1
fi

echo "Using JAVA_HOME: ${JAVA_HOME}" | tee -a "$LOG_FILE"

# Function to securely prompt for password
get_password() {
  while true; do
    echo -n "Enter password for keystore $1.p12 (Alias: $2): " > /dev/tty
    read -s ksPwd < /dev/tty
    echo "" > /dev/tty  # Move to a new line after password input

    if [ -z "$ksPwd" ]; then
      echo "Error: Password cannot be empty." > /dev/tty
    elif [ ${#ksPwd} -ge 6 ]; then
      break
    else
      echo "Error: Keystore password must be at least 6 characters." > /dev/tty
    fi
  done
}

# Read the response file line by line
while IFS= read -r line || [[ -n "$line" ]]; do
  [[ -z "$line" || "$line" == \#* ]] && continue

  if [[ "$line" == export* ]]; then
    eval "$line"
    continue
  fi

  tgtStore=$(echo "$line" | awk '{print $1}')
  certAlias=$(echo "$line" | awk '{print $2}')
  sigAlg=$(echo "$line" | awk '{print $3}')
  dName=$(echo "$line" | sed -n 's/[^"]*"//p' | sed 's/"$//')

  if [ -z "$tgtStore" ] || [ -z "$certAlias" ] || [ -z "$sigAlg" ] || [ -z "$dName" ]; then
    echo "Skipping invalid line: $line" | tee -a "$LOG_FILE"
    continue
  fi

  sigAlg="${sigAlg}withRSA"
  CertValidDays=${CERT_VALID_DAYS:-730}
  CertKeySize=${CERT_KEY_SIZE:-2048}

  if [ -z "$CERT_START_DATE" ]; then
    CERT_START_DATE=$(date +"%Y/%m/%d")
  fi

  TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
  OUTPUT_DIR="generated_certs/${tgtStore}_${TIMESTAMP}"
  mkdir -p "$OUTPUT_DIR"

  AdditionalGenArgs="-startdate $CERT_START_DATE"

  if [ -n "$CERT_SUBJECT_ALTERNATIVE_NAMES" ]; then
    AdditionalGenArgs="$AdditionalGenArgs -ext SAN=$CERT_SUBJECT_ALTERNATIVE_NAMES"
    echo "Added SubjectAlternativeName: $CERT_SUBJECT_ALTERNATIVE_NAMES" | tee -a "$LOG_FILE"
  fi

  get_password "$tgtStore" "$certAlias"

  echo "Generating certificate for alias: $certAlias in keystore: ${OUTPUT_DIR}/${tgtStore}.p12" | tee -a "$LOG_FILE"
  echo "  - Validity: $CertValidDays days" | tee -a "$LOG_FILE"
  echo "  - Key Size: $CertKeySize bits" | tee -a "$LOG_FILE"
  echo "  - Start Date: $CERT_START_DATE" | tee -a "$LOG_FILE"
  echo "  - Distinguished Name: \"$dName\"" | tee -a "$LOG_FILE"

  "$JAVA_HOME/bin/keytool" -genkeypair -alias $certAlias -validity $CertValidDays \
    -keyalg RSA -keysize $CertKeySize -sigalg $sigAlg \
    -keystore "${OUTPUT_DIR}/${tgtStore}.p12" -storepass "$ksPwd" -storetype pkcs12 \
    $AdditionalGenArgs -dname "$dName"

  if [ "$?" -ne 0 ]; then
    echo "Error: Failed to create keystore for $certAlias" | tee -a "$LOG_FILE"
    continue
  fi

  "$JAVA_HOME/bin/keytool" -export -rfc -file "${OUTPUT_DIR}/${certAlias}.cer" -alias $certAlias \
    -keystore "${OUTPUT_DIR}/${tgtStore}.p12" -storepass "$ksPwd" -storetype pkcs12

  if [ "$?" -ne 0 ]; then
    echo "Error: Failed to export certificate for $certAlias" | tee -a "$LOG_FILE"
    continue
  fi

  echo "Generated files in $OUTPUT_DIR:" | tee -a "$LOG_FILE"
  echo "  - PKCS12 keystore: ${tgtStore}.p12" | tee -a "$LOG_FILE"
  echo "  - Public Key File: ${certAlias}.cer" | tee -a "$LOG_FILE"
  echo ""

done < "$RESPONSE_FILE"

echo "========= Certificate Generation Completed at $(date) =========" | tee -a "$LOG_FILE"