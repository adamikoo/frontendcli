import os

def patch_file(path, old_bytes, new_bytes):
    with open(path, 'rb') as f:
        content = bytearray(f.read())
    idx = content.find(old_bytes)
    if idx == -1:
        print(f"FAILED to find target in {path}")
        return False
    padded = new_bytes + b'\x00' * (len(old_bytes) - len(new_bytes))
    content[idx:idx+len(old_bytes)] = padded
    with open(path, 'wb') as f:
        f.write(content)
    print(f"Patched {path} at {hex(idx)}: wrote {new_bytes}")
    return True

target = b'/data/data/com.termux/files/usr/lib\x00'
replacement = b'$ORIGIN\x00'

patch_file('app/src/main/assets/proot_arm64/proot', target, replacement)
patch_file('app/src/main/assets/proot_arm64/libtalloc.so.2', target, replacement)
patch_file('app/src/main/assets/proot_arm64/libandroid-shmem.so', target, replacement)
