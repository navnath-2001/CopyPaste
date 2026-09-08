import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore

cred = credentials.Certificate("clipboard-eee5b-firebase-adminsdk-fbsvc-bc17fb7984.json")

firebase_admin.initialize_app(cred)

db = firestore.client()

print("Firebase connected successfully!")