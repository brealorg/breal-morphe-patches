from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE = (
    ROOT
    / "extensions/boostforreddit/src/main/java/app/morphe/extension"
    / "boostforreddit/utils/BoostSearchBottomNavigation.java"
)


class BoostHomeTaskRootSourceContractTest(unittest.TestCase):
    """Issue #198: Home must work when MainActivity is not the task root."""

    def test_home_restarts_task_when_main_is_not_root(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        self.assertEqual(1, source.count("MORPHE_BOOST_HOME_TASK_ROOT_ISSUE198_V1"))

        match = re.search(
            r"private static boolean openHome\(Activity activity\) \{.*?\n    }\n",
            source,
            flags=re.S,
        )
        self.assertIsNotNone(match)
        open_home = match.group(0)

        # MainActivity.onCreate() finishes unless it is the task root.
        self.assertIn("isMainActivityTaskRoot(activity)", open_home)
        self.assertIn("Intent.FLAG_ACTIVITY_NEW_TASK", open_home)
        self.assertIn("Intent.FLAG_ACTIVITY_CLEAR_TASK", open_home)
        # The normal case keeps the existing Home instance.
        self.assertIn("Intent.FLAG_ACTIVITY_CLEAR_TOP", open_home)

    def test_task_root_lookup_defaults_to_stack_preserving_route(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        match = re.search(
            r"private static boolean isMainActivityTaskRoot\(Activity activity\) \{.*?\n    }\n",
            source,
            flags=re.S,
        )
        self.assertIsNotNone(match)
        lookup = match.group(0)
        self.assertIn("getAppTasks()", lookup)
        self.assertIn("activity.getTaskId()", lookup)
        self.assertIn(
            "// Unknown: keep the stack-preserving route.\n        return true;",
            lookup,
        )


if __name__ == "__main__":
    unittest.main()
