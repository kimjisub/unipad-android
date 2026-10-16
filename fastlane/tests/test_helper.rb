require "fastlane"
puts "fastlane #{Fastlane::VERSION}"
require "supply"
require "supply/options"
require "tempfile"

# Exercise the real Supply client against an in-memory Play service. Authentication
# is replaced before the lane runs, so no credentials or network access are used.
class FakePlay
  attr_reader :calls, :updated_track, :errors

  def initialize(track, failure: nil)
    @track = track
    @failure = Array(failure)
    @errors = {}
    @calls = []
  end

  def insert_edit(package)
    @calls << [:begin, package]
    fail_at(:begin)
    AndroidPublisher::AppEdit.new(id: "test-edit")
  end

  def get_edit_track(package, edit, track)
    @calls << [:read, package, edit, track]
    fail_at(:read)
    @track
  end

  # The original rollout uploader lists tracks before selecting a release.
  def list_edit_tracks(package, edit)
    @calls << [:read, package, edit, "production"]
    fail_at(:read)
    AndroidPublisher::TracksListResponse.new(tracks: [@track].compact)
  end

  # Play refuses a track that carries two releases with the same status.
  def update_edit_track(package, edit, track, object)
    @calls << [:update, package, edit, track]
    fail_at(:update)
    statuses = Array(object.releases).map(&:status)
    raise ArgumentError, "Duplicate release statuses #{statuses.inspect}" if statuses.uniq.length != statuses.length
    @updated_track = object
  end

  def commit_edit(package, edit, **options)
    @calls << [:commit, package, edit]
    fail_at(:commit)
  end

  def delete_edit(package, edit)
    @calls << [:abort, package, edit]
    fail_at(:abort)
  end

  private

  def fail_at(step)
    return unless @failure.include?(step)
    @errors[step] = RuntimeError.new("#{step} failed")
    raise @errors[step]
  end
end

def assert(value, message)
  raise message unless value
end

def release(status, fraction = nil, codes = ["123"])
  AndroidPublisher::TrackRelease.new(status: status, user_fraction: fraction,
                                    version_codes: codes, name: "Test release",
                                    release_notes: [AndroidPublisher::LocalizedText.new(language: "en-US", text: "Release notes")],
                                    in_app_update_priority: 3)
end

Fastlane.load_actions
fastfile = Fastlane::FastFile.new(File.expand_path("../Fastfile", __dir__))
# Keep checks against the original uploader offline when reproducing on the base commit.
fastfile.define_singleton_method(:upload_to_play_store) do |**options|
  action = Fastlane::Actions::UploadToPlayStoreAction
  action.run(FastlaneCore::Configuration.create(action.available_options, options))
end
TEST_WARNINGS = []
FastlaneCore::UI.define_singleton_method(:important) do |message|
  TEST_WARNINGS << message
  puts message
end

def fake_client(play)
  client = Supply::Client.allocate
  client.client = play
  Supply::Client.define_singleton_method(:make_from_config) { |**options| client }
  client
end

TEST_FASTFILE = fastfile
