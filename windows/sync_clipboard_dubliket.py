import os
import sys
import json
import time
import threading
import getpass

import requests
import pyperclip

import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore


# ============================================================
# COPYPAST CONFIGURATION
# ============================================================

PROJECT_ID = "clipboard-eee5b5"

DATABASE_ID = "clipboard"

AUTH_URL = (
    "https://identitytoolkit.googleapis.com/v1/"
    "accounts:signInWithPassword"
)


# ============================================================
# GLOBAL VARIABLES
# ============================================================

db = None

uid = None

id_token = None

last_pc_text = None

last_phone_timestamp = None

ignore_pc_text = False

running = True


# ============================================================
# FIND FIREBASE SERVICE ACCOUNT JSON
# ============================================================

def find_service_account():

    folder = os.path.dirname(
        os.path.abspath(__file__)
    )

    possible_files = []

    # Look in Python script folder
    try:

        for filename in os.listdir(folder):

            if (
                filename.endswith(".json")
                and (
                    "firebase" in filename.lower()
                    or "adminsdk" in filename.lower()
                )
            ):

                possible_files.append(
                    os.path.join(folder, filename)
                )

    except Exception:
        pass

    # Look in current folder
    try:

        for filename in os.listdir(os.getcwd()):

            if (
                filename.endswith(".json")
                and (
                    "firebase" in filename.lower()
                    or "adminsdk" in filename.lower()
                )
            ):

                possible_files.append(
                    os.path.join(
                        os.getcwd(),
                        filename
                    )
                )

    except Exception:
        pass

    # Remove duplicates
    possible_files = list(
        dict.fromkeys(possible_files)
    )

    if possible_files:

        return possible_files[0]

    return None


# ============================================================
# FIND GOOGLE SERVICES JSON
# ============================================================

def find_google_services_json():

    possible_paths = [

        r"D:\copypast2\app\google-services.json",

        os.path.join(
            os.path.dirname(
                os.path.abspath(__file__)
            ),
            "google-services.json"
        ),

        os.path.join(
            os.getcwd(),
            "google-services.json"
        )
    ]

    if getattr(sys, "frozen", False):

        exe_folder = os.path.dirname(
            os.path.abspath(sys.executable)
        )

        possible_paths.append(
            os.path.join(
                exe_folder,
                "google-services.json"
            )
        )

    for path in possible_paths:

        if os.path.exists(path):

            return path

    return None


# ============================================================
# GET FIREBASE WEB API KEY
# ============================================================

def get_api_key():

    json_path = find_google_services_json()

    if not json_path:

        print()
        print(
            "ERROR: google-services.json not found."
        )

        print(
            r"Expected:"
        )

        print(
            r"D:\copypast2\app\google-services.json"
        )

        print()

        return None

    try:

        with open(
            json_path,
            "r",
            encoding="utf-8"
        ) as file:

            config = json.load(file)

        clients = config.get(
            "client",
            []
        )

        if not clients:

            print(
                "ERROR: Firebase client configuration not found."
            )

            return None

        api_keys = clients[0].get(
            "api_key",
            []
        )

        if not api_keys:

            print(
                "ERROR: Firebase API key not found."
            )

            return None

        api_key = api_keys[0].get(
            "current_key"
        )

        if not api_key:

            print(
                "ERROR: Firebase API key is empty."
            )

            return None

        return api_key

    except Exception as error:

        print(
            "ERROR reading google-services.json:",
            error
        )

        return None


# ============================================================
# FIREBASE ADMIN INITIALIZATION
# ============================================================

def initialize_firestore():

    global db

    service_account_path = find_service_account()

    if not service_account_path:

        print()
        print(
            "ERROR: Firebase service-account JSON not found."
        )

        print()
        print(
            "Put your Firebase Admin SDK JSON file"
        )

        print(
            r"inside: D:\clipboard file"
        )

        print()

        return False

    try:

        print(
            "Firebase Admin file found:"
        )

        print(
            service_account_path
        )

        # Initialize only once
        if not firebase_admin._apps:

            cred = credentials.Certificate(
                service_account_path
            )

            firebase_admin.initialize_app(
                cred
            )

        db = firestore.client(
            database_id=DATABASE_ID
        )

        print(
            "Firebase Admin SDK connected successfully!"
        )

        print(
            f"Firestore database: {DATABASE_ID}"
        )

        print()

        return True

    except Exception as error:

        print()
        print(
            "Firebase Admin initialization failed:"
        )

        print(error)

        print()

        return False


# ============================================================
# FIREBASE LOGIN
# ============================================================

def firebase_login():

    global uid
    global id_token

    api_key = get_api_key()

    if not api_key:

        return False

    print()
    print("=" * 55)
    print("           CopyPast - Laptop Login")
    print("=" * 55)
    print()

    email = input(
        "Email: "
    ).strip()

    if not email:

        print(
            "Email cannot be empty."
        )

        return False

    password = getpass.getpass(
        "Password: "
    )

    if not password:

        print(
            "Password cannot be empty."
        )

        return False

    print()
    print(
        "Logging in..."
    )

    try:

        response = requests.post(

            f"{AUTH_URL}?key={api_key}",

            json={
                "email": email,
                "password": password,
                "returnSecureToken": True
            },

            timeout=15
        )

        data = response.json()

        if response.status_code != 200:

            error_message = (

                data
                .get("error", {})
                .get(
                    "message",
                    "Login failed"
                )
            )

            print()
            print(
                "LOGIN FAILED"
            )

            print(
                f"Reason: {error_message}"
            )

            print()

            return False

        # Firebase Authentication UID
        uid = data["localId"]

        # Keep token for login verification
        id_token = data["idToken"]

        print()
        print(
            "Login successful!"
        )

        print(
            f"User UID: {uid}"
        )

        print()

        return True

    except requests.RequestException as error:

        print()
        print(
            "Firebase connection failed:"
        )

        print(error)

        print()

        return False


# ============================================================
# USER CLIPBOARD COLLECTION
# ============================================================

def user_clipboard():

    if not uid:

        return None

    return (
        db
        .collection("users")
        .document(uid)
        .collection("clipboard")
    )


# ============================================================
# LAPTOP → PHONE
# ============================================================

def send_laptop_to_phone(text):

    if not db or not uid:

        return False

    try:

        timestamp = int(
            time.time() * 1000
        )

        user_clipboard() \
            .document("laptop_to_phone") \
            .set({

                "text": text,

                "device": "Laptop",

                "timestamp": timestamp

            })

        return True

    except Exception as error:

        print()
        print(
            "Laptop → Phone error:"
        )

        print(error)

        print()

        return False


# ============================================================
# PHONE → LAPTOP
# ============================================================

def get_phone_to_laptop():

    if not db or not uid:

        return None

    try:

        document = (
            user_clipboard()
            .document("phone_to_laptop")
            .get()
        )

        if not document.exists:

            return None

        return document.to_dict()

    except Exception as error:

        print()
        print(
            "Phone → Laptop error:"
        )

        print(error)

        print()

        return None


# ============================================================
# PHONE → LAPTOP MONITOR
# ============================================================

def phone_to_laptop_monitor():

    global last_phone_timestamp
    global ignore_pc_text

    print(
        "Phone → Laptop monitor started."
    )

    while running:

        try:

            document = get_phone_to_laptop()

            if document:

                device = document.get(
                    "device"
                )

                text = document.get(
                    "text"
                )

                timestamp = document.get(
                    "timestamp"
                )

                if (
                    device == "Phone"
                    and text
                ):

                    if (
                        timestamp is not None
                        and
                        timestamp != last_phone_timestamp
                    ):

                        last_phone_timestamp = timestamp

                        # Prevent sending the received
                        # phone text back to Firestore.
                        ignore_pc_text = True

                        pyperclip.copy(
                            text
                        )

                        print()
                        print(
                            "========================================"
                        )

                        print(
                            "New text received from PHONE"
                        )

                        print(
                            "========================================"
                        )

                        print(text)

                        print(
                            "========================================"
                        )

                        print()

            time.sleep(1)

        except Exception as error:

            print(
                "Phone monitor error:",
                error
            )

            time.sleep(2)


# ============================================================
# LAPTOP → PHONE MONITOR
# ============================================================

def laptop_to_phone_monitor():

    global last_pc_text
    global ignore_pc_text

    print(
        "Laptop → Phone monitor started."
    )

    try:

        last_pc_text = pyperclip.paste()

    except Exception:

        last_pc_text = ""

    while running:

        try:

            current_text = pyperclip.paste()

            if current_text is None:

                current_text = ""

            if current_text != last_pc_text:

                last_pc_text = current_text

                # Ignore clipboard text that came
                # from the phone.
                if ignore_pc_text:

                    ignore_pc_text = False

                    time.sleep(0.2)

                    continue

                if not current_text:

                    continue

                print()
                print(
                    "========================================"
                )

                print(
                    "New clipboard text:"
                )

                print(current_text)

                print(
                    "========================================"
                )

                success = (
                    send_laptop_to_phone(
                        current_text
                    )
                )

                if success:

                    print(
                        "Sent to phone successfully."
                    )

                else:

                    print(
                        "Failed to send text to phone."
                    )

            time.sleep(0.5)

        except Exception as error:

            print(
                "Laptop clipboard error:",
                error
            )

            time.sleep(2)


# ============================================================
# MAIN
# ============================================================

def main():

    global running

    print()
    print("=" * 55)

    print(
        "                 CopyPast"
    )

    print(
        "        Two-Way Clipboard Synchronization"
    )

    print("=" * 55)

    print()

    print(
        f"Firebase Project : {PROJECT_ID}"
    )

    print(
        f"Firestore DB     : {DATABASE_ID}"
    )

    print()

    # --------------------------------------------------------
    # LOGIN
    # --------------------------------------------------------

    if not firebase_login():

        print()
        print(
            "CopyPast could not start."
        )

        input(
            "Press Enter to close..."
        )

        return

    # --------------------------------------------------------
    # FIRESTORE CONNECTION
    # --------------------------------------------------------

    if not initialize_firestore():

        print()
        print(
            "CopyPast could not connect to Firestore."
        )

        input(
            "Press Enter to close..."
        )

        return

    # --------------------------------------------------------
    # START THREADS
    # --------------------------------------------------------

    phone_thread = threading.Thread(

        target=phone_to_laptop_monitor,

        daemon=True
    )

    laptop_thread = threading.Thread(

        target=laptop_to_phone_monitor,

        daemon=True
    )

    phone_thread.start()

    laptop_thread.start()

    print()
    print("=" * 55)

    print(
        "CopyPast Clipboard Sync is RUNNING"
    )

    print("=" * 55)

    print()

    print(
        "Logged-in UID:"
    )

    print(uid)

    print()

    print(
        "Copy text on the laptop → phone"
    )

    print(
        "Send phone clipboard → laptop"
    )

    print()

    print(
        "Press Ctrl+C to stop."
    )

    print()

    try:

        while True:

            time.sleep(1)

    except KeyboardInterrupt:

        print()
        print(
            "Stopping CopyPast..."
        )

        running = False

        time.sleep(1)

        print(
            "CopyPast stopped."
        )


# ============================================================
# START
# ============================================================

if __name__ == "__main__":

    main()
    