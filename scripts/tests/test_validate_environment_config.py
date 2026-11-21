from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from scripts.validate_environment_config import validate_file


class EnvironmentFileValidationTests(unittest.TestCase):
    def write_env(self, directory: str, content: str) -> Path:
        path = Path(directory) / ".env.example"
        path.write_text(content, encoding="utf-8")
        return path

    def valid_content(self, profile: str) -> str:
        values = {
            "SPRING_PROFILES_ACTIVE": profile,
            "JWT_SECRET": "<set-in-secret-manager>",
            "INTERNAL_API_KEY": "<set-in-secret-manager>",
            "POSTGRES_DB": "social_media_ai_db",
            "POSTGRES_USER": "<set-in-secret-manager>",
            "POSTGRES_PASSWORD": "<set-in-secret-manager>",
        }
        if profile != "dev":
            values["GATEWAY_PUBLIC_URL"] = f"https://api-{profile}.example.com"
        return "\n".join(f"{key}={value}" for key, value in values.items())

    def test_rejects_missing_profile(self):
        with TemporaryDirectory() as directory:
            path = self.write_env(
                directory,
                self.valid_content("dev").replace("SPRING_PROFILES_ACTIVE=dev\n", ""),
            )

            self.assertIn("SPRING_PROFILES_ACTIVE is required", validate_file(path))

    def test_rejects_missing_non_dev_secrets(self):
        with TemporaryDirectory() as directory:
            path = self.write_env(
                directory,
                self.valid_content("staging").replace("JWT_SECRET=<set-in-secret-manager>\n", ""),
            )

            self.assertIn("JWT_SECRET is required", validate_file(path))

    def test_rejects_real_looking_secret_in_non_dev_sample(self):
        with TemporaryDirectory() as directory:
            path = self.write_env(
                directory,
                self.valid_content("prod").replace(
                    "JWT_SECRET=<set-in-secret-manager>",
                    "JWT_SECRET=correct-horse-battery-staple-production-key",
                ),
            )

            self.assertIn(
                "JWT_SECRET must be a placeholder in a sample file",
                validate_file(path),
            )

    def test_accepts_all_repository_samples(self):
        repository_root = Path(__file__).resolve().parents[2]

        for relative_path in (
            "backend/.env.example",
            "backend/.env.staging.example",
            "backend/.env.prod.example",
        ):
            with self.subTest(relative_path=relative_path):
                self.assertEqual(
                    validate_file(repository_root / relative_path),
                    [],
                )


if __name__ == "__main__":
    unittest.main()
