#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from boost_extension_linkage import ClassDef, Model, check, unresolved  # noqa: E402

CALL = "Lokhttp3/Call;"
EXT = "Lapp/morphe/extension/boostforreddit/http/HttpUtils;"
OBJECT_CLASS = "Ljava/lang/Object;"


def boost_model() -> Model:
    """Stock-Boost-like classes: okio is obfuscated, so Call.timeout returns okio.b1."""
    model = Model()
    model.classes[CALL] = ClassDef(OBJECT_CLASS, ["Ljava/lang/Cloneable;"], {("timeout", "()Lokio/b1;")})
    model.classes["Lokio/b1;"] = ClassDef(OBJECT_CLASS, [])
    model.classes["Lokhttp3/OkHttpClient$Builder;"] = ClassDef(OBJECT_CLASS, [], {
        ("callTimeout", "(JLjava/util/concurrent/TimeUnit;)Lokhttp3/OkHttpClient$Builder;")})
    model.classes["Lcom/boost/Settings;"] = ClassDef("Landroid/app/Activity;", [])
    model.classes["Lcom/boost/Base;"] = ClassDef(OBJECT_CLASS, [], {("load", "()V")}, {("count", "I")})
    model.classes["Lcom/boost/Child;"] = ClassDef("Lcom/boost/Base;", [])
    return model


def with_extension(*refs: tuple[str, str, str, str]) -> Model:
    model = boost_model()
    model.classes[EXT] = ClassDef(OBJECT_CLASS, [])
    model.refs.update(refs)
    return model


class BoostExtensionLinkageTest(unittest.TestCase):
    def test_obfuscated_return_type_is_reported(self) -> None:
        # Issue #219: compiled against stock okio, Boost has okio.Timeout renamed.
        candidate = with_extension(
            ("method", CALL, "timeout", "()Lokio/Timeout;"),
            ("class", "Lokio/Timeout;", "", ""),
        )
        self.assertEqual(
            check(boost_model(), candidate),
            [("class", "Lokio/Timeout;", "", ""), ("method", CALL, "timeout", "()Lokio/Timeout;")],
        )

    def test_existing_signature_passes(self) -> None:
        candidate = with_extension(
            ("method", "Lokhttp3/OkHttpClient$Builder;", "callTimeout",
             "(JLjava/util/concurrent/TimeUnit;)Lokhttp3/OkHttpClient$Builder;"),
            ("method", CALL, "timeout", "()Lokio/b1;"),
        )
        self.assertEqual(check(boost_model(), candidate), [])

    def test_framework_interface_does_not_hide_missing_method(self) -> None:
        # Call implements Cloneable; that must not make arbitrary methods resolve.
        candidate = with_extension(("method", CALL, "execute", "()Lokhttp3/Response;"))
        self.assertEqual(len(check(boost_model(), candidate)), 1)

    def test_object_only_provides_its_own_methods(self) -> None:
        candidate = with_extension(
            ("method", CALL, "toString", "()Ljava/lang/String;"),
            ("method", CALL, "cancel", "()V"),
        )
        self.assertEqual(check(boost_model(), candidate), [("method", CALL, "cancel", "()V")])

    def test_framework_superclass_is_trusted(self) -> None:
        candidate = with_extension(("method", "Lcom/boost/Settings;", "getWindow", "()Landroid/view/Window;"))
        self.assertEqual(check(boost_model(), candidate), [])

    def test_inherited_members_resolve(self) -> None:
        candidate = with_extension(
            ("method", "Lcom/boost/Child;", "load", "()V"),
            ("field", "Lcom/boost/Child;", "count", "I"),
            ("field", "Lcom/boost/Child;", "count", "J"),
        )
        self.assertEqual(check(boost_model(), candidate), [("field", "Lcom/boost/Child;", "count", "J")])

    def test_missing_library_class_reported_but_framework_and_annotations_ignored(self) -> None:
        candidate = with_extension(
            ("class", "Lcom/google/gson/Gson;", "", ""),
            ("class", "Landroid/view/View;", "", ""),
            ("class", "Landroidx/annotation/Nullable;", "", ""),
        )
        self.assertEqual(check(boost_model(), candidate), [("class", "Lcom/google/gson/Gson;", "", "")])

    def test_refs_already_unresolved_in_stock_boost_are_ignored(self) -> None:
        legacy = ("method", "Lcom/boost/Base;", "gone", "()V")
        base = boost_model()
        base.refs.add(legacy)
        candidate = with_extension(legacy)
        self.assertEqual(unresolved(candidate), {legacy})
        self.assertEqual(check(base, candidate), [])

    def test_extension_defined_members_resolve(self) -> None:
        candidate = with_extension(("method", EXT, "getJson", "(Ljava/lang/String;J)V"))
        candidate.classes[EXT].methods.add(("getJson", "(Ljava/lang/String;J)V"))
        self.assertEqual(check(boost_model(), candidate), [])


if __name__ == "__main__":
    unittest.main()
