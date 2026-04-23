import sys, urllib.request
try:
    urllib.request.urlopen("http://127.0.0.1:8022/index.html", timeout=0.2)
except Exception:
    sys.exit(1)
