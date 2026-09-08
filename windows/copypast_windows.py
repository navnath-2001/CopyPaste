import os
import sys
import json
import time
import base64
import hashlib
import threading
import tkinter as tk
from tkinter import messagebox
from io import BytesIO

import requests
import pyperclip
import keyring

import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore

from PIL import Image, ImageGrab, ImageDraw

import pystray

import win32clipboard


# ============================================================
# COPYPAST CONFIGURATION
# ============================================================

PROJECT_ID = "clipboard-eee5b5"

DATABASE_ID = "clipboard"

DEVICE_NAME = "Laptop"

AUTH_URL = (
    "https://identitytoolkit.googleapis.com/v1/"
    "accounts:signInWithPassword"
)

REGISTER_URL = (
    "https://identitytoolkit.googleapis.com/v1/"
    "accounts:signUp"
)

REFRESH_URL = (
    "https://securetoken.googleapis.com/v1/token"
)

KEYRING_SERVICE = "CopyPast"

# Firestore document limit is 1 MiB.
# Keep chunks safely below that limit.
IMAGE_CHUNK_SIZE = 400_000


# ============================================================
# GLOBAL VARIABLES
# ============================================================

db = None

uid = None

email = None

id_token = None

refresh_token = None

running = False

logged_in = False

sync_enabled = True

root = None

tray_icon = None

status_label = None

last_pc_text = ""

last_pc_image_hash = None

last_phone_timestamp = None

last_phone_image_id = None


# ============================================================
# FIND GOOGLE-SERVICES.JSON
# ============================================================

def find_google_services_json():

    possible_paths = []

    # Current Python file folder
    script_folder = os.path.dirname(
        os.path.abspath(__file__)
    )

    possible_paths.append(
        os.path.join(
            script_folder,
            "google-services.json"
        )
    )

    # Current working folder
    possible_paths.append(
        os.path.join(
            os.getcwd(),
            "google-services.json"
        )
    )

    # Your Android project
    possible_paths.append(
        r"D:\copypast2\app\google-services.json"
    )

    # If running as EXE:
    # D:\clipboard file\dist\CopyPast.exe
    # Also check:
    # D:\clipboard file\
    if getattr(sys, "frozen", False):

        exe_folder = os.path.dirname(
            os.path.abspath(
                sys.executable
            )
        )

        possible_paths.append(
            os.path.join(
                exe_folder,
                "google-services.json"
            )
        )

        parent_folder = os.path.dirname(
            exe_folder
        )

        possible_paths.append(
            os.path.join(
                parent_folder,
                "google-services.json"
            )
        )

    for path in possible_paths:

        if os.path.exists(path):

            return path

    return None


# ============================================================
# GET FIREBASE API KEY
# ============================================================

def get_api_key():

    json_path = (
        find_google_services_json()
    )

    if not json_path:

        print(
            "ERROR: google-services.json not found."
        )

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

            return None

        api_keys = clients[0].get(
            "api_key",
            []
        )

        if not api_keys:

            return None

        return api_keys[0].get(
            "current_key"
        )

    except Exception as error:

        print(
            "API key error:",
            error
        )

        return None


# ============================================================
# FIND FIREBASE ADMIN SERVICE ACCOUNT
# ============================================================

def find_service_account():

    folders = []

    # Python script folder
    script_folder = os.path.dirname(
        os.path.abspath(__file__)
    )

    folders.append(
        script_folder
    )

    # Current working directory
    folders.append(
        os.getcwd()
    )

    # If running as EXE,
    # check EXE folder and parent folder.
    if getattr(sys, "frozen", False):

        exe_folder = os.path.dirname(
            os.path.abspath(
                sys.executable
            )
        )

        folders.append(
            exe_folder
        )

        folders.append(
            os.path.dirname(
                exe_folder
            )
        )

    # Remove duplicates
    folders = list(
        dict.fromkeys(folders)
    )

    for folder in folders:

        try:

            for filename in os.listdir(
                folder
            ):

                lower = filename.lower()

                if (
                    lower.endswith(".json")
                    and (
                        "firebase" in lower
                        or
                        "adminsdk" in lower
                    )
                ):

                    return os.path.join(
                        folder,
                        filename
                    )

        except Exception:

            pass

    return None


# ============================================================
# INITIALIZE FIRESTORE
# ============================================================

def initialize_firestore():

    global db

    try:

        service_account_path = (
            find_service_account()
        )

        if not service_account_path:

            print(
                "ERROR: Firebase Admin SDK JSON not found."
            )

            print(
                "Put the service-account JSON inside:"
            )

            print(
                r"D:\clipboard file"
            )

            return False

        print(
            "Firebase Admin file:"
        )

        print(
            service_account_path
        )

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

        return True

    except Exception as error:

        print(
            "Firebase initialization error:"
        )

        print(error)

        return False


# ============================================================
# FIRESTORE USER REFERENCE
# ============================================================

def user_ref():

    if not db or not uid:

        return None

    return (
        db
        .collection("users")
        .document(uid)
    )


# ============================================================
# CLIPBOARD COLLECTION
# ============================================================

def clipboard_ref():

    user = user_ref()

    if not user:

        return None

    return user.collection(
        "clipboard"
    )


# ============================================================
# SESSION REFERENCE
# ============================================================

def session_ref():

    user = user_ref()

    if not user:

        return None

    return (
        user
        .collection("session")
        .document("status")
    )


# ============================================================
# SET SHARED SESSION STATUS
# ============================================================

def set_session_status(
    logged_status
):

    ref = session_ref()

    if not ref:

        return False

    try:

        ref.set({

            "loggedIn":
                logged_status,

            "device":
                DEVICE_NAME,

            "timestamp":
                int(
                    time.time() * 1000
                )

        })

        return True

    except Exception as error:

        print(
            "Session status error:",
            error
        )

        return False


# ============================================================
# GET SHARED SESSION STATUS
# ============================================================

def get_session_status():

    ref = session_ref()

    if not ref:

        return None

    try:

        snapshot = ref.get()

        if not snapshot.exists:

            return None

        return snapshot.to_dict()

    except Exception as error:

        print(
            "Session read error:",
            error
        )

        return None


# ============================================================
# FIREBASE LOGIN
# ============================================================

def firebase_login_user(
    user_email,
    user_password
):

    global uid
    global email
    global id_token
    global refresh_token

    api_key = get_api_key()

    if not api_key:

        return (
            False,
            "Firebase API key not found."
        )

    try:

        response = requests.post(

            f"{AUTH_URL}?key={api_key}",

            json={

                "email":
                    user_email,

                "password":
                    user_password,

                "returnSecureToken":
                    True

            },

            timeout=15
        )

        data = response.json()

        if response.status_code != 200:

            message = (
                data
                .get("error", {})
                .get(
                    "message",
                    "Login failed"
                )
            )

            return (
                False,
                message
            )

        uid = data[
            "localId"
        ]

        email = user_email

        id_token = data[
            "idToken"
        ]

        refresh_token = data[
            "refreshToken"
        ]

        return (
            True,
            "Login successful."
        )

    except Exception as error:

        return (
            False,
            str(error)
        )


# ============================================================
# CREATE FIREBASE ACCOUNT
# ============================================================

def firebase_register_user(
    user_email,
    user_password
):

    api_key = get_api_key()

    if not api_key:

        return (
            False,
            "Firebase API key not found."
        )

    try:

        response = requests.post(

            f"{REGISTER_URL}?key={api_key}",

            json={

                "email":
                    user_email,

                "password":
                    user_password,

                "returnSecureToken":
                    True

            },

            timeout=15
        )

        data = response.json()

        if response.status_code != 200:

            message = (
                data
                .get("error", {})
                .get(
                    "message",
                    "Account creation failed"
                )
            )

            return (
                False,
                message
            )

        return (
            True,
            "Account created."
        )

    except Exception as error:

        return (
            False,
            str(error)
        )


# ============================================================
# REMEMBER ME
# ============================================================

def save_refresh_token(
    user_email,
    token
):

    try:

        keyring.set_password(

            KEYRING_SERVICE,

            user_email,

            token
        )

        return True

    except Exception as error:

        print(
            "Credential save error:",
            error
        )

        return False


def load_refresh_token(
    user_email
):

    try:

        return keyring.get_password(

            KEYRING_SERVICE,

            user_email
        )

    except Exception:

        return None


def delete_refresh_token(
    user_email
):

    if not user_email:

        return

    try:

        keyring.delete_password(

            KEYRING_SERVICE,

            user_email
        )

    except Exception:

        pass


# ============================================================
# REMEMBERED EMAIL FILE
# ============================================================

def email_config_path():

    return os.path.join(

        os.path.expanduser("~"),

        ".copypast_email"
    )


def save_remembered_email(
    user_email
):

    try:

        with open(
            email_config_path(),
            "w",
            encoding="utf-8"
        ) as file:

            file.write(
                user_email
            )

    except Exception as error:

        print(
            "Email save error:",
            error
        )


def load_remembered_email():

    try:

        path = email_config_path()

        if not os.path.exists(path):

            return None

        with open(
            path,
            "r",
            encoding="utf-8"
        ) as file:

            value = file.read().strip()

        return value or None

    except Exception:

        return None


def delete_remembered_email():

    try:

        path = email_config_path()

        if os.path.exists(path):

            os.remove(path)

    except Exception:

        pass


# ============================================================
# REFRESH FIREBASE SESSION
# ============================================================

def refresh_firebase_session(
    stored_email,
    stored_refresh_token
):

    global uid
    global email
    global id_token
    global refresh_token

    api_key = get_api_key()

    if not api_key:

        return False

    try:

        response = requests.post(

            f"{REFRESH_URL}?key={api_key}",

            data={

                "grant_type":
                    "refresh_token",

                "refresh_token":
                    stored_refresh_token

            },

            timeout=15
        )

        data = response.json()

        if response.status_code != 200:

            return False

        id_token = data[
            "id_token"
        ]

        refresh_token = data[
            "refresh_token"
        ]

        uid = data[
            "user_id"
        ]

        email = stored_email

        return True

    except Exception as error:

        print(
            "Session refresh error:",
            error
        )

        return False


# ============================================================
# DELETE COMPLETE TRANSFER
# ============================================================

def delete_transfer(
    direction
):

    collection = clipboard_ref()

    if not collection:

        return False

    parent = (
        collection
        .document(direction)
    )

    try:

        # Delete all image chunks first.
        chunks = (
            parent
            .collection("chunks")
            .stream()
        )

        for chunk in chunks:

            chunk.reference.delete()

        # Delete transfer metadata.
        parent.delete()

        print(
            f"Temporary transfer deleted: "
            f"{direction}"
        )

        return True

    except Exception as error:

        print(
            f"Delete transfer error "
            f"({direction}):"
        )

        print(error)

        return False


# ============================================================
# SEND TEXT
# ============================================================

def send_text(
    direction,
    text,
    device_name
):

    if not running:

        return False

    if not sync_enabled:

        return False

    collection = clipboard_ref()

    if not collection:

        return False

    if not text:

        return False

    try:

        # Remove old temporary transfer.
        delete_transfer(
            direction
        )

        parent = (
            collection
            .document(direction)
        )

        parent.set({

            "type":
                "text",

            "text":
                text,

            "device":
                device_name,

            "timestamp":
                int(
                    time.time() * 1000
                )

        })

        print(
            "Temporary text transfer created."
        )

        return True

    except Exception as error:

        print(
            "Text send error:"
        )

        print(error)

        return False


# ============================================================
# IMAGE → PNG BYTES
# ============================================================

def image_to_bytes(
    image
):

    output = BytesIO()

    image.save(

        output,

        format="PNG",

        optimize=True
    )

    return output.getvalue()


# ============================================================
# IMAGE HASH
# ============================================================

def get_image_hash(
    image
):

    try:

        data = image_to_bytes(
            image
        )

        return hashlib.sha256(
            data
        ).hexdigest()

    except Exception:

        return None


# ============================================================
# SEND IMAGE
# ============================================================

def send_image(
    direction,
    image,
    device_name
):

    if not running:

        return False

    if not sync_enabled:

        return False

    collection = clipboard_ref()

    if not collection:

        return False

    try:

        image_bytes = image_to_bytes(
            image
        )

        encoded = base64.b64encode(
            image_bytes
        ).decode(
            "ascii"
        )

        image_id = (

            str(
                int(
                    time.time() * 1000
                )
            )

            + "_"

            + hashlib.sha256(
                image_bytes
            ).hexdigest()[:12]

        )

        chunks = [

            encoded[
                start:
                start + IMAGE_CHUNK_SIZE
            ]

            for start in range(
                0,
                len(encoded),
                IMAGE_CHUNK_SIZE
            )
        ]

        parent = (
            collection
            .document(direction)
        )

        # Delete old temporary transfer.
        delete_transfer(
            direction
        )

        # ----------------------------------------------------
        # UPLOAD CHUNKS
        # ----------------------------------------------------

        for index, chunk in enumerate(
            chunks
        ):

            (
                parent
                .collection("chunks")
                .document(
                    str(index)
                )
                .set({

                    "data":
                        chunk

                })
            )

        # ----------------------------------------------------
        # WRITE METADATA LAST
        # ----------------------------------------------------

        parent.set({

            "type":
                "image",

            "imageId":
                image_id,

            "chunks":
                len(chunks),

            "mimeType":
                "image/png",

            "size":
                len(image_bytes),

            "device":
                device_name,

            "timestamp":
                int(
                    time.time() * 1000
                )

        })

        print(
            f"Temporary image transfer created "
            f"({len(chunks)} chunks)."
        )

        return True

    except Exception as error:

        print(
            "Image send error:"
        )

        print(error)

        return False


# ============================================================
# DOWNLOAD IMAGE
# ============================================================

def download_transfer_image(
    direction,
    metadata
):

    collection = clipboard_ref()

    if not collection:

        return None

    try:

        parent = (
            collection
            .document(direction)
        )

        chunk_count = int(
            metadata.get(
                "chunks",
                0
            )
        )

        if chunk_count <= 0:

            return None

        parts = []

        for index in range(
            chunk_count
        ):

            snapshot = (

                parent
                .collection("chunks")
                .document(
                    str(index)
                )
                .get()

            )

            if not snapshot.exists:

                print(
                    f"Missing image chunk: {index}"
                )

                return None

            chunk_data = (
                snapshot
                .to_dict()
                .get("data")
            )

            if not chunk_data:

                return None

            parts.append(
                chunk_data
            )

        encoded = "".join(
            parts
        )

        image_bytes = (
            base64.b64decode(
                encoded
            )
        )

        image = Image.open(
            BytesIO(image_bytes)
        )

        image.load()

        return image

    except Exception as error:

        print(
            "Image download error:"
        )

        print(error)

        return None


# ============================================================
# GET WINDOWS CLIPBOARD IMAGE
# ============================================================

def get_windows_clipboard_image():

    try:

        result = (
            ImageGrab.grabclipboard()
        )

        if isinstance(
            result,
            Image.Image
        ):

            return result.copy()

    except Exception as error:

        print(
            "Clipboard image error:",
            error
        )

    return None


# ============================================================
# SET WINDOWS IMAGE CLIPBOARD
# ============================================================

def set_windows_image_clipboard(
    image
):

    output = BytesIO()

    image.convert(
        "RGB"
    ).save(
        output,
        "BMP"
    )

    bmp_data = output.getvalue()

    # Remove BMP file header.
    dib_data = bmp_data[14:]

    win32clipboard.OpenClipboard()

    try:

        win32clipboard.EmptyClipboard()

        win32clipboard.SetClipboardData(

            win32clipboard.CF_DIB,

            dib_data
        )

    finally:

        win32clipboard.CloseClipboard()


# ============================================================
# PHONE → LAPTOP MONITOR
# ============================================================

def phone_to_laptop_monitor():

    global last_phone_timestamp
    global last_phone_image_id

    print(
        "Phone → Laptop monitor started."
    )

    while running:

        try:

            if not sync_enabled:

                time.sleep(1)

                continue

            collection = clipboard_ref()

            if not collection:

                time.sleep(1)

                continue

            parent = (
                collection
                .document(
                    "phone_to_laptop"
                )
            )

            snapshot = parent.get()

            if not snapshot.exists:

                time.sleep(0.5)

                continue

            data = snapshot.to_dict()

            if not data:

                time.sleep(0.5)

                continue

            # Only accept Phone transfers.
            if data.get(
                "device"
            ) != "Phone":

                time.sleep(0.5)

                continue

            transfer_type = data.get(
                "type",
                "text"
            )

            # =================================================
            # TEXT
            # =================================================

            if transfer_type == "text":

                timestamp = data.get(
                    "timestamp"
                )

                text = data.get(
                    "text"
                )

                if (
                    timestamp is not None
                    and
                    timestamp !=
                    last_phone_timestamp
                    and
                    text
                ):

                    last_phone_timestamp = (
                        timestamp
                    )

                    # Put into Windows clipboard.
                    pyperclip.copy(
                        text
                    )

                    print(
                        "Phone → Laptop:"
                    )

                    print(
                        "Text received."
                    )

                    # IMPORTANT:
                    # Delete only AFTER
                    # clipboard update succeeded.
                    delete_transfer(
                        "phone_to_laptop"
                    )

            # =================================================
            # IMAGE
            # =================================================

            elif transfer_type == "image":

                image_id = data.get(
                    "imageId"
                )

                if (
                    image_id
                    and
                    image_id !=
                    last_phone_image_id
                ):

                    image = (
                        download_transfer_image(
                            "phone_to_laptop",
                            data
                        )
                    )

                    if image:

                        # Put image into
                        # Windows clipboard.
                        set_windows_image_clipboard(
                            image
                        )

                        last_phone_image_id = (
                            image_id
                        )

                        print(
                            "Phone → Laptop:"
                        )

                        print(
                            "Image received."
                        )

                        # IMPORTANT:
                        # Delete after successful
                        # clipboard operation.
                        delete_transfer(
                            "phone_to_laptop"
                        )

            time.sleep(0.5)

        except Exception as error:

            print(
                "Phone → Laptop monitor error:"
            )

            print(error)

            time.sleep(2)


# ============================================================
# LAPTOP → PHONE MONITOR
# ============================================================

def laptop_to_phone_monitor():

    global last_pc_text
    global last_pc_image_hash

    print(
        "Laptop → Phone monitor started."
    )

    try:

        last_pc_text = (
            pyperclip.paste()
            or ""
        )

    except Exception:

        last_pc_text = ""

    while running:

        try:

            if not sync_enabled:

                time.sleep(0.5)

                continue

            # =================================================
            # CHECK IMAGE FIRST
            # =================================================

            image = (
                get_windows_clipboard_image()
            )

            if image:

                current_hash = (
                    get_image_hash(
                        image
                    )
                )

                if (
                    current_hash
                    and
                    current_hash !=
                    last_pc_image_hash
                ):

                    last_pc_image_hash = (
                        current_hash
                    )

                    print(
                        "Laptop → Phone:"
                    )

                    print(
                        "New image detected."
                    )

                    send_image(

                        "laptop_to_phone",

                        image,

                        "Laptop"
                    )

                    time.sleep(0.5)

                    continue

            # =================================================
            # CHECK TEXT
            # =================================================

            try:

                current_text = (
                    pyperclip.paste()
                    or ""
                )

            except Exception:

                current_text = ""

            if (
                current_text !=
                last_pc_text
            ):

                last_pc_text = (
                    current_text
                )

                if current_text:

                    print(
                        "Laptop → Phone:"
                    )

                    print(
                        "New text detected."
                    )

                    send_text(

                        "laptop_to_phone",

                        current_text,

                        "Laptop"
                    )

            time.sleep(0.5)

        except Exception as error:

            print(
                "Laptop clipboard monitor error:"
            )

            print(error)

            time.sleep(2)


# ============================================================
# SHARED SESSION MONITOR
# ============================================================

def session_monitor():

    print(
        "Shared session monitor started."
    )

    while running:

        try:

            status = (
                get_session_status()
            )

            if status:

                logged = status.get(
                    "loggedIn"
                )

                device = status.get(
                    "device"
                )

                # Phone logged out.
                if (
                    logged is False
                    and
                    device != "Laptop"
                ):

                    print(
                        "Remote logout detected."
                    )

                    root.after(
                        0,
                        remote_logout
                    )

                    return

            time.sleep(2)

        except Exception as error:

            print(
                "Session monitor error:"
            )

            print(error)

            time.sleep(3)


# ============================================================
# START SYNC
# ============================================================

def start_sync():

    global running

    if running:

        return

    running = True

    set_session_status(
        True
    )

    threads = [

        threading.Thread(
            target=
            phone_to_laptop_monitor,
            daemon=True
        ),

        threading.Thread(
            target=
            laptop_to_phone_monitor,
            daemon=True
        ),

        threading.Thread(
            target=
            session_monitor,
            daemon=True
        )

    ]

    for thread in threads:

        thread.start()

    update_status(
        "Connected • Sync ON"
    )


# ============================================================
# STOP SYNC
# ============================================================

def stop_sync():

    global running

    running = False

    update_status(
        "Sync stopped"
    )


# ============================================================
# LOGOUT
# ============================================================

def logout():

    global logged_in
    global uid
    global email
    global id_token
    global refresh_token

    if not logged_in:

        return

    # Tell Android to logout too.
    try:

        set_session_status(
            False
        )

    except Exception:

        pass

    # Stop clipboard monitoring.
    stop_sync()

    # Delete remembered credentials.
    if email:

        delete_refresh_token(
            email
        )

    delete_remembered_email()

    logged_in = False

    uid = None

    email = None

    id_token = None

    refresh_token = None

    root.deiconify()

    show_login_screen()


# ============================================================
# REMOTE LOGOUT
# ============================================================

def remote_logout():

    global logged_in
    global uid
    global email
    global id_token
    global refresh_token
    global running

    running = False

    logged_in = False

    if email:

        delete_refresh_token(
            email
        )

    delete_remembered_email()

    uid = None

    email = None

    id_token = None

    refresh_token = None

    root.deiconify()

    messagebox.showinfo(

        "CopyPast",

        "You were logged out from another device."
    )

    show_login_screen()


# ============================================================
# TOGGLE SYNC
# ============================================================

def toggle_sync():

    global sync_enabled

    sync_enabled = not sync_enabled

    if sync_enabled:

        update_status(
            "Connected • Sync ON"
        )

    else:

        update_status(
            "Connected • Sync OFF"
        )


# ============================================================
# UPDATE STATUS
# ============================================================

def update_status(
    text
):

    if root and status_label:

        try:

            root.after(

                0,

                lambda:
                status_label.config(
                    text=text
                )

            )

        except Exception:

            pass


# ============================================================
# TRAY - OPEN
# ============================================================

def tray_open(
    icon,
    item
):

    try:

        root.after(
            0,
            root.deiconify
        )

        root.after(
            0,
            root.lift
        )

    except Exception:

        pass


# ============================================================
# TRAY - LOGOUT
# ============================================================

def tray_logout(
    icon,
    item
):

    try:

        root.after(
            0,
            logout
        )

    except Exception:

        pass


# ============================================================
# TRAY - EXIT
# ============================================================

def tray_exit(
    icon,
    item
):

    try:

        root.after(
            0,
            exit_application
        )

    except Exception:

        pass


# ============================================================
# CREATE TRAY ICON
# ============================================================

def create_tray():

    global tray_icon

    image = Image.new(

        "RGB",

        (64, 64),

        "white"
    )

    draw = ImageDraw.Draw(
        image
    )

    draw.rounded_rectangle(

        (4, 4, 60, 60),

        radius=14,

        fill="#6750A4"
    )

    draw.text(

        (16, 20),

        "CP",

        fill="white"
    )

    menu = pystray.Menu(

        pystray.MenuItem(

            "Open CopyPast",

            tray_open
        ),

        pystray.MenuItem(

            "Logout",

            tray_logout
        ),

        pystray.MenuItem(

            "Exit",

            tray_exit
        )
    )

    tray_icon = pystray.Icon(

        "CopyPast",

        image,

        "CopyPast",

        menu
    )

    threading.Thread(

        target=
        tray_icon.run,

        daemon=True

    ).start()


# ============================================================
# WINDOW CLOSE
# ============================================================

def on_window_close():

    # If logged in:
    # hide to tray instead of exiting.
    if logged_in:

        root.withdraw()

    else:

        exit_application()


# ============================================================
# EXIT APPLICATION
# ============================================================

def exit_application():

    global running

    running = False

    try:

        if tray_icon:

            tray_icon.stop()

    except Exception:

        pass

    try:

        root.destroy()

    except Exception:

        pass

    # Completely terminate Windows process.
    os._exit(0)


# ============================================================
# LOGIN SCREEN
# ============================================================

def show_login_screen():

    global status_label

    for widget in root.winfo_children():

        widget.destroy()

    root.title(
        "CopyPast - Login"
    )

    root.geometry(
        "430x560"
    )

    root.resizable(
        False,
        False
    )

    frame = tk.Frame(

        root,

        bg="#F7F7FB"
    )

    frame.pack(

        fill="both",

        expand=True
    )

    tk.Label(

        frame,

        text="CopyPast",

        font=(
            "Segoe UI",
            32,
            "bold"
        ),

        fg="#6750A4",

        bg="#F7F7FB"

    ).pack(
        pady=(55, 5)
    )

    tk.Label(

        frame,

        text="Sync your clipboard everywhere",

        font=(
            "Segoe UI",
            11
        ),

        fg="#666666",

        bg="#F7F7FB"

    ).pack(
        pady=(0, 35)
    )

    card = tk.Frame(

        frame,

        bg="white",

        padx=25,

        pady=25
    )

    card.pack(

        padx=35,

        fill="x"
    )

    tk.Label(

        card,

        text="Email",

        bg="white",

        font=(
            "Segoe UI",
            10
        )

    ).pack(
        anchor="w"
    )

    email_entry = tk.Entry(

        card,

        font=(
            "Segoe UI",
            11
        )
    )

    email_entry.pack(

        fill="x",

        pady=(5, 15),

        ipady=7
    )

    tk.Label(

        card,

        text="Password",

        bg="white",

        font=(
            "Segoe UI",
            10
        )

    ).pack(
        anchor="w"
    )

    password_entry = tk.Entry(

        card,

        show="•",

        font=(
            "Segoe UI",
            11
        )
    )

    password_entry.pack(

        fill="x",

        pady=(5, 15),

        ipady=7
    )

    remember_var = tk.BooleanVar(
        value=True
    )

    tk.Checkbutton(

        card,

        text="Remember me",

        variable=remember_var,

        bg="white",

        activebackground="white",

        font=(
            "Segoe UI",
            9
        )

    ).pack(

        anchor="w",

        pady=(0, 15)
    )

    status_label = tk.Label(

        card,

        text="",

        bg="white",

        fg="#D32F2F",

        wraplength=330,

        font=(
            "Segoe UI",
            9
        )
    )

    status_label.pack(
        pady=(0, 10)
    )

    # --------------------------------------------------------
    # LOGIN
    # --------------------------------------------------------

    def do_login():

        global logged_in

        user_email = (
            email_entry
            .get()
            .strip()
        )

        password = (
            password_entry
            .get()
        )

        if not user_email:

            status_label.config(

                text="Enter your email.",

                fg="#D32F2F"
            )

            return

        if not password:

            status_label.config(

                text="Enter your password.",

                fg="#D32F2F"
            )

            return

        status_label.config(

            text="Logging in...",

            fg="#555555"
        )

        root.update()

        success, message = (
            firebase_login_user(

                user_email,

                password
            )
        )

        if not success:

            status_label.config(

                text=message,

                fg="#D32F2F"
            )

            return

        # ----------------------------------------------------
        # REMEMBER ME
        # ----------------------------------------------------

        if remember_var.get():

            save_refresh_token(

                user_email,

                refresh_token
            )

            save_remembered_email(
                user_email
            )

        else:

            delete_refresh_token(
                user_email
            )

            delete_remembered_email()

        # ----------------------------------------------------
        # SHARED SESSION
        # ----------------------------------------------------

        set_session_status(
            True
        )

        logged_in = True

        show_main_screen()

        start_sync()

    # --------------------------------------------------------
    # REGISTER
    # --------------------------------------------------------

    def create_account():

        show_register_screen()

    tk.Button(

        card,

        text="LOGIN",

        command=do_login,

        bg="#6750A4",

        fg="white",

        activebackground="#59439A",

        activeforeground="white",

        font=(
            "Segoe UI",
            10,
            "bold"
        ),

        relief="flat",

        cursor="hand2"

    ).pack(

        fill="x",

        ipady=8
    )

    tk.Button(

        card,

        text="CREATE NEW ACCOUNT",

        command=create_account,

        bg="#3F51B5",

        fg="white",

        activebackground="#303F9F",

        activeforeground="white",

        font=(
            "Segoe UI",
            9,
            "bold"
        ),

        relief="flat",

        cursor="hand2"

    ).pack(

        fill="x",

        ipady=8,

        pady=(10, 0)
    )

    email_entry.focus()


# ============================================================
# REGISTER SCREEN
# ============================================================

def show_register_screen():

    global status_label

    for widget in root.winfo_children():

        widget.destroy()

    root.title(
        "CopyPast - Create Account"
    )

    root.geometry(
        "430x600"
    )

    root.resizable(
        False,
        False
    )

    frame = tk.Frame(

        root,

        bg="#F7F7FB"
    )

    frame.pack(

        fill="both",

        expand=True
    )

    tk.Label(

        frame,

        text="Create Account",

        font=(
            "Segoe UI",
            27,
            "bold"
        ),

        fg="#6750A4",

        bg="#F7F7FB"

    ).pack(
        pady=(50, 30)
    )

    card = tk.Frame(

        frame,

        bg="white",

        padx=25,

        pady=25
    )

    card.pack(

        padx=35,

        fill="x"
    )

    tk.Label(

        card,

        text="Email",

        bg="white",

        font=(
            "Segoe UI",
            10
        )

    ).pack(
        anchor="w"
    )

    email_entry = tk.Entry(

        card,

        font=(
            "Segoe UI",
            11
        )
    )

    email_entry.pack(

        fill="x",

        pady=(5, 15),

        ipady=7
    )

    tk.Label(

        card,

        text="Password",

        bg="white",

        font=(
            "Segoe UI",
            10
        )

    ).pack(
        anchor="w"
    )

    password_entry = tk.Entry(

        card,

        show="•",

        font=(
            "Segoe UI",
            11
        )
    )

    password_entry.pack(

        fill="x",

        pady=(5, 15),

        ipady=7
    )

    tk.Label(

        card,

        text="Confirm Password",

        bg="white",

        font=(
            "Segoe UI",
            10
        )

    ).pack(
        anchor="w"
    )

    confirm_entry = tk.Entry(

        card,

        show="•",

        font=(
            "Segoe UI",
            11
        )
    )

    confirm_entry.pack(

        fill="x",

        pady=(5, 15),

        ipady=7
    )

    status_label = tk.Label(

        card,

        text="",

        bg="white",

        fg="#D32F2F",

        wraplength=330,

        font=(
            "Segoe UI",
            9
        )
    )

    status_label.pack(
        pady=(0, 10)
    )

    def register():

        user_email = (
            email_entry
            .get()
            .strip()
        )

        password = (
            password_entry
            .get()
        )

        confirm = (
            confirm_entry
            .get()
        )

        if not user_email:

            status_label.config(

                text="Email is required."
            )

            return

        if len(password) < 6:

            status_label.config(

                text=(
                    "Password must be at least "
                    "6 characters."
                )
            )

            return

        if password != confirm:

            status_label.config(

                text="Passwords do not match."
            )

            return

        status_label.config(

            text="Creating account...",

            fg="#555555"
        )

        root.update()

        success, message = (
            firebase_register_user(

                user_email,

                password
            )
        )

        if not success:

            status_label.config(

                text=message,

                fg="#D32F2F"
            )

            return

        messagebox.showinfo(

            "CopyPast",

            "Account created successfully."
        )

        show_login_screen()

    tk.Button(

        card,

        text="CREATE ACCOUNT",

        command=register,

        bg="#6750A4",

        fg="white",

        font=(
            "Segoe UI",
            10,
            "bold"
        ),

        relief="flat"

    ).pack(

        fill="x",

        ipady=8
    )

    tk.Button(

        card,

        text="BACK TO LOGIN",

        command=show_login_screen,

        bg="#3F51B5",

        fg="white",

        font=(
            "Segoe UI",
            9,
            "bold"
        ),

        relief="flat"

    ).pack(

        fill="x",

        ipady=8,

        pady=(10, 0)
    )


# ============================================================
# MAIN SCREEN
# ============================================================

def show_main_screen():

    global status_label

    for widget in root.winfo_children():

        widget.destroy()

    root.title(
        "CopyPast"
    )

    root.geometry(
        "460x520"
    )

    root.resizable(
        False,
        False
    )

    frame = tk.Frame(

        root,

        bg="#F7F7FB"
    )

    frame.pack(

        fill="both",

        expand=True
    )

    tk.Label(

        frame,

        text="CopyPast",

        font=(
            "Segoe UI",
            30,
            "bold"
        ),

        fg="#6750A4",

        bg="#F7F7FB"

    ).pack(
        pady=(40, 5)
    )

    tk.Label(

        frame,

        text="Laptop ↔ Phone Clipboard",

        font=(
            "Segoe UI",
            11
        ),

        fg="#666666",

        bg="#F7F7FB"

    ).pack(
        pady=(0, 25)
    )

    card = tk.Frame(

        frame,

        bg="white",

        padx=30,

        pady=25
    )

    card.pack(

        padx=35,

        fill="x"
    )

    tk.Label(

        card,

        text="● Connected",

        font=(
            "Segoe UI",
            12,
            "bold"
        ),

        fg="#2E7D32",

        bg="white"

    ).pack(
        pady=(0, 10)
    )

    status_label = tk.Label(

        card,

        text="Connected • Sync ON",

        font=(
            "Segoe UI",
            10
        ),

        fg="#555555",

        bg="white"
    )

    status_label.pack(
        pady=(0, 20)
    )

    tk.Button(

        card,

        text="ON / OFF SYNC",

        command=toggle_sync,

        bg="#6750A4",

        fg="white",

        font=(
            "Segoe UI",
            10,
            "bold"
        ),

        relief="flat",

        cursor="hand2"

    ).pack(

        fill="x",

        ipady=9
    )

    tk.Button(

        card,

        text="LOGOUT",

        command=logout,

        bg="#D32F2F",

        fg="white",

        font=(
            "Segoe UI",
            10,
            "bold"
        ),

        relief="flat",

        cursor="hand2"

    ).pack(

        fill="x",

        ipady=9,

        pady=(10, 0)
    )

    tk.Label(

        frame,

        text=(
            "Close X to keep CopyPast running "
            "in the system tray."
        ),

        font=(
            "Segoe UI",
            9
        ),

        fg="#777777",

        bg="#F7F7FB"

    ).pack(
        pady=25
    )


# ============================================================
# AUTO LOGIN
# ============================================================

def try_auto_login():

    global logged_in

    stored_email = (
        load_remembered_email()
    )

    if not stored_email:

        return

    stored_token = (
        load_refresh_token(
            stored_email
        )
    )

    if not stored_token:

        return

    print(
        "Trying Remember Me login..."
    )

    if not refresh_firebase_session(

        stored_email,

        stored_token

    ):

        print(
            "Saved session expired."
        )

        delete_refresh_token(
            stored_email
        )

        delete_remembered_email()

        return

    # --------------------------------------------------------
    # CHECK SHARED LOGOUT
    # --------------------------------------------------------

    status = (
        get_session_status()
    )

    if (
        status
        and
        status.get(
            "loggedIn"
        ) is False
    ):

        print(
            "Shared logout was detected."
        )

        delete_refresh_token(
            stored_email
        )

        delete_remembered_email()

        return

    # Save rotated refresh token.
    save_refresh_token(

        stored_email,

        refresh_token
    )

    # Mark laptop online.
    set_session_status(
        True
    )

    logged_in = True

    show_main_screen()

    start_sync()

    print(
        "Remember Me login successful."
    )


# ============================================================
# MAIN
# ============================================================

def main():

    global root

    print()
    print(
        "=" * 60
    )

    print(
        "                    CopyPast"
    )

    print(
        "          Two-Way Clipboard Synchronization"
    )

    print(
        "=" * 60
    )

    print()

    print(
        f"Firebase Project : {PROJECT_ID}"
    )

    print(
        f"Firestore DB     : {DATABASE_ID}"
    )

    print()

    # --------------------------------------------------------
    # FIRESTORE
    # --------------------------------------------------------

    if not initialize_firestore():

        print(
            "CopyPast could not connect to Firestore."
        )

        return

    # --------------------------------------------------------
    # TKINTER
    # --------------------------------------------------------

    root = tk.Tk()

    root.configure(
        bg="#F7F7FB"
    )

    root.protocol(

        "WM_DELETE_WINDOW",

        on_window_close
    )

    # --------------------------------------------------------
    # SYSTEM TRAY
    # --------------------------------------------------------

    create_tray()

    # --------------------------------------------------------
    # LOGIN SCREEN
    # --------------------------------------------------------

    show_login_screen()

    # --------------------------------------------------------
    # REMEMBER ME
    # --------------------------------------------------------

    root.after(

        700,

        try_auto_login
    )

    # --------------------------------------------------------
    # START GUI
    # --------------------------------------------------------

    root.mainloop()


# ============================================================
# START
# ============================================================

if __name__ == "__main__":

    main()