"""Unit tests use an explicit, non-production internal credential."""

import os

os.environ["INTERNAL_API_KEY"] = "test-internal-key-not-for-production"
