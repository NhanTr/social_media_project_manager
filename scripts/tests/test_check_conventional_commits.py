from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest

from scripts.check_conventional_commits import find_invalid_commits


class ConventionalCommitTests(unittest.TestCase):
    def setUp(self):
        self.temporary_directory = TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.repository = Path(self.temporary_directory.name)
        self.git("init", "-b", "main")
        self.git("config", "user.email", "ci@example.com")
        self.git("config", "user.name", "CI")
        self.commit("chore: initialize repository")
        self.base = self.git("rev-parse", "HEAD")

    def git(self, *arguments: str) -> str:
        result = subprocess.run(
            ["git", *arguments],
            cwd=self.repository,
            check=True,
            capture_output=True,
            text=True,
        )
        return result.stdout.strip()

    def commit(self, subject: str):
        self.git("commit", "--allow-empty", "-m", subject)

    def invalid_commits(self):
        return find_invalid_commits(self.base, "HEAD", cwd=self.repository)

    def test_accepts_conventional_subjects(self):
        for subject in (
            "feat: add profile page",
            "fix(auth): rotate refresh token",
            "docs: explain deployment",
        ):
            self.commit(subject)

        self.assertEqual(self.invalid_commits(), [])

    def test_rejects_subject_without_a_valid_type(self):
        self.commit("Update deployment docs")

        invalid = self.invalid_commits()

        self.assertEqual(len(invalid), 1)
        self.assertEqual(invalid[0].subject, "Update deployment docs")

    def test_rejects_empty_subject(self):
        self.git("commit", "--allow-empty", "--allow-empty-message", "-m", "")

        invalid = self.invalid_commits()

        self.assertEqual(len(invalid), 1)
        self.assertEqual(invalid[0].subject, "")

    def test_accepts_breaking_change_marker(self):
        self.commit("feat(api)!: remove legacy endpoint")

        self.assertEqual(self.invalid_commits(), [])

    def test_ignores_standard_merge_commits(self):
        self.git("switch", "-c", "feature")
        self.commit("feat: add feature")
        self.git("switch", "main")
        self.commit("docs: update main branch")
        self.git("merge", "--no-ff", "feature", "-m", "Merge branch 'feature'")

        self.assertEqual(self.invalid_commits(), [])


if __name__ == "__main__":
    unittest.main()
