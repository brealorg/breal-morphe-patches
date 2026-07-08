# Boost DEV runtime policy

Boost runtime validation should use the DEV-clone lane first.

Rules:

1. Prefer com.rubenmayayo.reddit.dev for runtime testing.
2. Do not install over normal Boost unless DEV-clone cannot validate the path and the reason is documented.
3. Do not trust ANDROID_SERIAL; wireless-debugging ports rotate.
4. Resolve the current device through tools/boost-adb-serial.sh.
5. Use MORPHE_ADB_HINT, not a fixed port, when a selector is needed.
6. Open repro URLs directly in Boost Dev when possible.
7. Treat AndroidRuntime from com.android.commands.monkey as launch-tool noise unless target-app evidence also exists.
8. Runtime release gates must include actual user-flow evidence, not build success only.

Standard helper:

    tools/boost-dev-issue-runtime.sh \
      --name issueXX-short-name \
      --url "https://www.reddit.com/..." \
      --marker "issue_specific_marker"

Expected success marker:

    RESULT=MORPHE_BOOST_DEV_ISSUE_RUNTIME_OK
