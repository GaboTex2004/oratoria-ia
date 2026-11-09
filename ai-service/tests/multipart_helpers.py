import uuid


def _build_multipart_body(
    fields: list[tuple[str, str]],
    file_field: tuple[str, str, bytes, str],
) -> tuple[bytes, str]:
    boundary = f"----OratoriaAIBoundary{uuid.uuid4().hex}"
    chunks: list[bytes] = []

    for name, value in fields:
        chunks.append(f"--{boundary}\r\n".encode("utf-8"))
        chunks.append(f'Content-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode("utf-8"))

    field_name, filename, file_bytes, content_type = file_field
    safe_filename = filename.replace('"', "").replace("\r", "").replace("\n", "") or "audio.webm"
    chunks.append(f"--{boundary}\r\n".encode("utf-8"))
    chunks.append(
        (
            f'Content-Disposition: form-data; name="{field_name}"; filename="{safe_filename}"\r\n'
            f"Content-Type: {content_type}\r\n\r\n"
        ).encode("utf-8")
    )
    chunks.append(file_bytes)
    chunks.append(f"\r\n--{boundary}--\r\n".encode("utf-8"))

    return b"".join(chunks), f"multipart/form-data; boundary={boundary}"
