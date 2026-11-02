# Select only a prebuilt for the requested Android platform and Java bindings.
# A different platform can require unavailable linker features; another TDLib
# revision aborts in JNI_OnLoad before the app can start.
function(resolve_tdlib_library tdlib_dir ndk_revision platform abi output_variable)
  if(NOT platform MATCHES "^android-[0-9]+$")
    message(FATAL_ERROR "Invalid Android platform: ${platform}")
  endif()
  set(version_file "${tdlib_dir}/version.txt")
  set(java_file "${tdlib_dir}/src/main/java/org/drinkless/tdlib/TdApi.java")
  if(NOT EXISTS "${version_file}" OR NOT EXISTS "${java_file}")
    message(FATAL_ERROR "Missing TDLib version.txt or TdApi.java in ${tdlib_dir}")
  endif()

  file(READ "${version_file}" expected_commit)
  string(STRIP "${expected_commit}" expected_commit)
  string(LENGTH "${expected_commit}" commit_length)
  if(NOT commit_length EQUAL 40 OR NOT expected_commit MATCHES "^[0-9a-f]+$")
    message(FATAL_ERROR "Invalid TDLib commit in ${version_file}")
  endif()
  file(STRINGS "${java_file}" java_version_line
    REGEX "^[ \t]*private static final String GIT_COMMIT_HASH = \"${expected_commit}\";"
    LIMIT_COUNT 1)
  if(NOT java_version_line)
    message(FATAL_ERROR "TdApi.java does not match TDLib version.txt (${expected_commit})")
  endif()

  set(candidates
    "${tdlib_dir}/src/main/libs/${ndk_revision}/${platform}/${abi}/libtdjni.so"
    "${tdlib_dir}/src/main/libs/${ndk_revision}/${platform}/libs/${abi}/libtdjni.so"
  )
  if(NOT CMAKE_SCRIPT_MODE_FILE)
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${version_file}" "${java_file}")
  endif()
  foreach(candidate IN LISTS candidates)
    if(EXISTS "${candidate}")
      if(NOT CMAKE_SCRIPT_MODE_FILE)
        set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${candidate}")
      endif()
      # The JNI API generator embeds its exact commit as a standalone string.
      # Keep TDLib's runtime check intact; never rewrite its Java hash.
      file(STRINGS "${candidate}" matching_commit
        REGEX "^${expected_commit}$" LIMIT_COUNT 1)
      if(matching_commit)
        set(${output_variable} "${candidate}" PARENT_SCOPE)
        message(STATUS "Using TDLib ${expected_commit}: ${candidate}")
        return()
      endif()
    endif()
  endforeach()
  message(FATAL_ERROR
    "No compatible libtdjni.so for TDLib ${expected_commit}, NDK ${ndk_revision}, platform ${platform}, ABI ${abi}. Checked: ${candidates}")
endfunction()
