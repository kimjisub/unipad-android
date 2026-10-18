# Run: ruby fastlane/tests/deploy_promote_test.rb (using the same gem environment as fastlane).
require_relative "test_helper"

# Stop at the upload boundary: no build, version bump, credentials or Play access.
# Rejected options must never reach any of these operations.
operations = []
TEST_FASTFILE.define_singleton_method(:upload_to_play_store) do |**options|
  operations << [:upload, options]
end
TEST_FASTFILE.define_singleton_method(:gradle) { |**options| operations << [:gradle, options] }
TEST_FASTFILE.define_singleton_method(:bump_version_code!) { operations << [:bump] }
TEST_FASTFILE.define_singleton_method(:lane_context) { {} }
ENV["PLAY_JSON_KEY_FILE"] = __FILE__

cases = []
[10, "10", 50, "50", 1.5, "1.5", 0, "0", -0.1, "-0.1", "abc", "0.5oops", "", nil, false, "NaN", "Infinity"].each do |value|
  cases << [:promote, { fraction: value }, :invalid]
  %w[production internal].each do |track|
    cases << [:deploy, { track: track, rollout: value }, :invalid]
  end
end
cases << [:deploy, { track: "production" }, :invalid]
[0.1, "0.1", 1, "1"].each do |value|
  cases << [:promote, { fraction: value }, Float(value)]
  cases << [:deploy, { track: "production", rollout: value }, Float(value)]
  cases << [:deploy, { track: "internal", rollout: value }, Float(value)]
end
cases << [:promote, {}, 0.1]
%w[internal alpha beta].each { |track| cases << [:deploy, { track: track }, nil] }
cases << [:deploy, {}, nil]

failures = []
cases.each do |lane, options, expected|
  name = "#{lane} #{options.inspect}"
  begin
    operations.clear
    TEST_SUCCESSES.clear
    error = nil
    begin
      TEST_FASTFILE.runner.lanes[:android][lane].call(options)
    rescue StandardError => e
      error = e
    end
    if expected == :invalid
      assert(operations.empty?, "Invalid fraction reached build or Play: #{operations.inspect}")
      assert(error && error.message.include?("Pass fraction as a decimal between 0 and 1 (e.g. 0.5 = 50%, 1 = complete)"), "Expected fraction error, got #{error.inspect}")
      assert(TEST_SUCCESSES.empty?, "Rejected fraction reported success")
    else
      assert(error.nil?, "Unexpected error: #{error.inspect}")
      uploads = operations.select { |step| step.first == :upload }
      assert(uploads.size == 1, "Expected one upload, got #{operations.inspect}")
      upload = uploads.first[1]
      status = expected == 1 ? "completed" : "inProgress"
      if lane == :promote
        assert(operations.map(&:first) == [:upload], "Promotion must not build")
        assert(upload[:track] == "internal" && upload[:track_promote_to] == "production", "Wrong promotion tracks")
        assert(upload[:track_promote_release_status] == status, "Wrong promotion status")
      else
        status = "draft" unless options[:track] == "production"
        assert(operations.map(&:first) == [:bump, :gradle, :gradle, :gradle, :upload], "Deploy build sequence changed")
        assert(upload[:track] == (options[:track] || "internal"), "Wrong deploy track")
        assert(upload[:release_status] == status, "Wrong deploy status")
      end
      actual_fraction = upload[:rollout] && Float(upload[:rollout])
      assert(actual_fraction == expected, "Expected fraction #{expected.inspect}, got #{upload[:rollout].inspect}")
      assert(TEST_SUCCESSES.size == 1, "Successful upload must report success once")
    end
    puts "PASS: #{name}"
  rescue StandardError => e
    failures << name
    warn "FAIL: #{name}: #{e.message}"
  end
end
puts "#{cases.length} cases, #{failures.length} failures"
exit(failures.empty? ? 0 : 1)
