from pathlib import Path
import json
import subprocess
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_PATH = REPOSITORY_ROOT / ".github/workflows/ci.yml"


def load_workflow() -> dict:
    workflow_source = WORKFLOW_PATH.read_text(encoding="utf-8")
    # macOS ships an older YAML 1.1 parser that does not accept GitHub's
    # YAML 1.2 flow-style port scalar. Quote only that scalar for parsing.
    workflow_source = workflow_source.replace(
        "ports: [5432:5432]", 'ports: ["5432:5432"]'
    )
    result = subprocess.run(
        [
            "ruby",
            "-ryaml",
            "-rjson",
            "-e",
            "puts JSON.generate(YAML.load(STDIN.read))",
        ],
        check=True,
        capture_output=True,
        text=True,
        input=workflow_source,
    )
    return json.loads(result.stdout)


class ContinuousIntegrationContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = load_workflow()
        cls.steps = [
            step
            for job in cls.workflow["jobs"].values()
            for step in job.get("steps", [])
        ]

    def run_commands(self) -> str:
        return "\n".join(step.get("run", "") for step in self.steps)

    def test_validates_environment_samples(self):
        self.assertIn(
            "python3 scripts/validate_environment_config.py",
            self.run_commands(),
        )

    def test_validates_commit_subjects_for_pull_requests_and_pushes(self):
        commands = self.run_commands()
        self.assertIn("python3 scripts/check_conventional_commits.py", commands)
        self.assertIn(
            "${{ github.event.pull_request.base.sha }}..${{ github.event.pull_request.head.sha }}",
            commands,
        )
        self.assertIn(
            "${{ github.event.before }}..${{ github.sha }}",
            commands,
        )

    def test_builds_flutter_debug_apk(self):
        self.assertIn("flutter build apk --debug", self.run_commands())

    def test_runs_backend_lint_and_verify(self):
        commands = self.run_commands()
        self.assertIn("mvn -B -DskipTests checkstyle:check", commands)
        self.assertIn("mvn -B clean verify", commands)

    def test_blocks_high_or_critical_dependency_findings(self):
        trivy_steps = [
            step
            for step in self.steps
            if step.get("uses") == "aquasecurity/trivy-action@0.29.0"
        ]
        self.assertEqual(len(trivy_steps), 1)
        configuration = trivy_steps[0].get("with", {})
        self.assertEqual(configuration.get("scan-type"), "fs")
        self.assertEqual(configuration.get("severity"), "HIGH,CRITICAL")
        self.assertEqual(str(configuration.get("ignore-unfixed")).lower(), "true")
        self.assertEqual(str(configuration.get("exit-code")), "1")


if __name__ == "__main__":
    unittest.main()
