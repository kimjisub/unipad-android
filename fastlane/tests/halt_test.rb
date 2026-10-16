# Run: ruby fastlane/tests/halt_test.rb (using the same gem environment as fastlane).
require "fastlane"
require "supply"
require "supply/options"
require "tempfile"

# Exercise the real Supply client against an in-memory Play service. Authentication
# is replaced before the lane runs, so no credentials or network access are used.
class FakePlay
  attr_reader :calls, :updated_track

  def initialize(track, failure: nil)
    @track = track
    @failure = failure
    @calls = []
  end

  def insert_edit(package)
    @calls << [:begin, package]
    AndroidPublisher::AppEdit.new(id: "test-edit")
  end

  def get_edit_track(package, edit, track)
    @calls << [:read, package, edit, track]
    @track
  end

  def update_edit_track(package, edit, track, object)
    @calls << [:update, package, edit, track]
    raise "update failed" if @failure == :update
    @updated_track = object
  end

  def commit_edit(package, edit, **options)
    @calls << [:commit, package, edit]
    raise "commit failed" if @failure == :commit
  end

  def delete_edit(package, edit)
    @calls << [:abort, package, edit]
  end
end

def assert(value, message)
  raise message unless value
end

def release(status, fraction = nil, codes = ["123"])
  AndroidPublisher::TrackRelease.new(status: status, user_fraction: fraction,
                                    version_codes: codes, name: "Test release")
end

Fastlane.load_actions
fastfile = Fastlane::FastFile.new(File.expand_path("../Fastfile", __dir__))
# Run the old uploader too, using the same fake service, to prove that its empty
# edit commits without changing the track. This also keeps the baseline offline.
fastfile.define_singleton_method(:upload_to_play_store) do |**options|
  action = Fastlane::Actions::UploadToPlayStoreAction
  action.run(FastlaneCore::Configuration.create(action.available_options, options))
end
client = nil
Supply::Client.define_singleton_method(:make_from_config) { |**options| client }

cases = []
[0.1, 0.37, 0.999].each do |fraction|
  cases << ["preserves fraction #{fraction}", release("inProgress", fraction), nil, nil]
end
cases.concat([
  ["completed release", release("completed"), "completed", nil],
  ["already halted", release("halted", 0.1), "inProgress", nil],
  ["draft release", release("draft"), "inProgress", nil],
  ["missing release", nil, "not found", nil],
  ["wrong version", release("inProgress", 0.1, ["999"]), "not found", nil],
  ["missing fraction", release("inProgress"), "fraction", nil],
  ["zero fraction", release("inProgress", 0.0), "fraction", nil],
  ["full fraction", release("inProgress", 1.0), "fraction", nil],
  ["update error", release("inProgress", 0.1), "update failed", :update],
  ["commit error", release("inProgress", 0.1), "commit failed", :commit],
  ["multi-code release", release("inProgress", 0.25, ["122", "123"]), nil, nil]
])

failures = []
Tempfile.create(["fake-play", ".json"]) do |key|
  key.write("{}")
  key.flush
  ENV["PLAY_JSON_KEY_FILE"] = key.path
  cases.each do |name, target, expected_error, failure|
    begin
      previous = release("completed", nil, ["121"])
      track = AndroidPublisher::Track.new(track: "production", releases: [previous, target].compact)
      before = previous.to_h
      target_before = target&.to_h
      fraction = target&.user_fraction
      play = FakePlay.new(track, failure: failure)
      client = Supply::Client.allocate
      client.client = play
      error = nil
      begin
        fastfile.runner.lanes[:android][:halt].call(version_code: "123")
      rescue StandardError => e
        error = e
      end
      if expected_error
        assert(error && error.message.include?(expected_error), "Expected #{expected_error.inspect}, got #{error.inspect}")
        assert(play.calls.last&.first == :abort, "Failed halt must discard its edit")
        assert(play.calls.none? { |call| call.first == :commit }, "Invalid release must not commit") unless failure == :commit
        assert(play.calls.none? { |call| call.first == :update }, "Invalid release must not update") unless failure
      else
        assert(error.nil?, "Unexpected error: #{error.inspect}")
        assert(play.calls.map(&:first) == [:begin, :read, :update, :commit], "Expected a real track update and commit, got #{play.calls.inspect}")
        assert(target.status == "halted", "Release must be halted")
        assert(target.user_fraction == fraction, "Rollout fraction changed")
        assert(target.to_h == target_before.merge(status: "halted"), "Release fields other than status changed")
        assert(play.updated_track.releases.include?(target), "Requested release missing from update")
        assert(previous.to_h == before, "Unrelated release changed")
        assert(play.calls.first[1] == "com.kimjisub.launchpad", "Wrong package")
      end
      puts "PASS: #{name}"
    rescue StandardError => e
      failures << name
      warn "FAIL: #{name}: #{e.message}"
    end
  end
end
puts "#{cases.length} cases, #{failures.length} failures"
exit(failures.empty? ? 0 : 1)
