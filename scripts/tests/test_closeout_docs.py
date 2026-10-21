from pathlib import Path
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
DOCUMENTS = [
    REPOSITORY_ROOT / "docs/architecture/01-mvp-boundaries-and-architecture.md",
    REPOSITORY_ROOT / "docs/sprint-0-1-followup.md",
    REPOSITORY_ROOT / "backend/docs/05-gaps-and-roadmap.md",
    REPOSITORY_ROOT / "README.md",
]
REQUIRED_STATEMENTS = [
    "MVP chấp nhận schema `public` dùng chung với ownership/grants.",
    "Schema riêng theo service được hoãn sang sprint hậu MVP.",
    "Mock/demo không production-ready.",
    "Adapter production AI, payment, email và push được hoãn sang Icebox.",
]


class CloseoutDocumentationContractTests(unittest.TestCase):
    def test_all_closeout_documents_state_the_same_mvp_deferrals(self):
        for document in DOCUMENTS:
            with self.subTest(document=document.relative_to(REPOSITORY_ROOT)):
                content = document.read_text(encoding="utf-8")
                for statement in REQUIRED_STATEMENTS:
                    self.assertIn(statement, content)

    def test_no_closeout_document_calls_mocks_production_ready(self):
        forbidden = (
            "mock production-ready",
            "mocks production-ready",
            "mock là production-ready",
        )
        for document in DOCUMENTS:
            with self.subTest(document=document.relative_to(REPOSITORY_ROOT)):
                content = document.read_text(encoding="utf-8").lower()
                for phrase in forbidden:
                    self.assertNotIn(phrase, content)


if __name__ == "__main__":
    unittest.main()
