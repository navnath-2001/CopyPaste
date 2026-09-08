import pyperclip
import time

last_text = ""

print("Clipboard Sync is running...")
print("Copy some text on your laptop.")

while True:
    current_text = pyperclip.paste()

    if current_text != last_text:
        print("New clipboard text:", current_text)
        last_text = current_text

    time.sleep(0.5)