"""
Lima Segura - YouTube Media Worker (Python)
Runs on a local machine with residential IP to fetch YouTube captions/transcriptions.
Polls the backend API for pending videos, fetches subtitles/transcriptions,
and sends content back to the backend.

Requirements:
    pip install requests youtube-transcript-api

Usage:
    BACKEND_URL=http://localhost:8080 WORKER_TOKEN=your-token python worker.py
"""

import os
import sys
import time
import logging
import requests
from youtube_transcript_api import YouTubeTranscriptApi
from youtube_transcript_api._errors import (
    TranscriptsDisabled,
    NoTranscriptFound,
    VideoUnavailable,
)

# Configuration
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080")
WORKER_TOKEN = os.getenv("WORKER_TOKEN", "")
POLL_INTERVAL = int(os.getenv("POLL_INTERVAL", "60"))  # seconds

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("media-worker")

HEADERS = {
    "Authorization": f"Bearer {WORKER_TOKEN}",
    "Content-Type": "application/json",
}


def get_pending_videos():
    """Fetch pending video items from the backend."""
    try:
        resp = requests.get(
            f"{BACKEND_URL}/api/v1/internal/worker/pending-videos",
            headers=HEADERS,
            timeout=30,
        )
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        logger.error(f"Failed to fetch pending videos: {e}")
        return []


def get_transcript(video_id: str) -> str | None:
    """Try to get Spanish subtitles for a YouTube video."""
    try:
        # Try Spanish auto-generated first, then any Spanish variant
        transcript_list = YouTubeTranscriptApi.list_transcripts(video_id)

        transcript = None
        # Try generated Spanish
        try:
            transcript = transcript_list.find_generated_transcript(["es"])
        except NoTranscriptFound:
            pass

        # Try manual Spanish
        if transcript is None:
            try:
                transcript = transcript_list.find_manually_created_transcript(["es"])
            except NoTranscriptFound:
                pass

        # Try any Spanish variant
        if transcript is None:
            try:
                transcript = transcript_list.find_generated_transcript(
                    ["es-419", "es-US", "es-MX", "es-PE"]
                )
            except NoTranscriptFound:
                pass

        if transcript is None:
            logger.warning(f"No Spanish transcript found for video {video_id}")
            return None

        entries = transcript.fetch()
        text = " ".join(entry["text"] for entry in entries)
        return text

    except TranscriptsDisabled:
        logger.warning(f"Transcripts disabled for video {video_id}")
        return None
    except VideoUnavailable:
        logger.warning(f"Video unavailable: {video_id}")
        return None
    except Exception as e:
        logger.error(f"Error getting transcript for {video_id}: {e}")
        return None


def submit_content(source_item_id: int, content_text: str):
    """Send transcribed content back to the backend."""
    try:
        resp = requests.post(
            f"{BACKEND_URL}/api/v1/internal/worker/source-items/{source_item_id}/content",
            headers=HEADERS,
            json={"contentText": content_text},
            timeout=30,
        )
        resp.raise_for_status()
        logger.info(f"Submitted content for source item {source_item_id}")
    except Exception as e:
        logger.error(f"Failed to submit content for {source_item_id}: {e}")


def process_video(item: dict):
    """Process a single pending video item."""
    source_item_id = item["id"]
    external_id = item["externalId"]  # This is the YouTube videoId
    title = item.get("title", "Unknown")

    logger.info(f"Processing video: {title} (videoId: {external_id})")

    transcript = get_transcript(external_id)

    if transcript and len(transcript.strip()) > 50:
        submit_content(source_item_id, transcript)
    else:
        logger.warning(
            f"No usable transcript for video {external_id}, skipping for now"
        )


def main():
    """Main loop: poll for pending videos and process them."""
    if not WORKER_TOKEN:
        logger.error("WORKER_TOKEN environment variable is required")
        sys.exit(1)

    logger.info(f"Starting media worker. Backend: {BACKEND_URL}")
    logger.info(f"Poll interval: {POLL_INTERVAL}s")

    while True:
        try:
            videos = get_pending_videos()

            if videos:
                logger.info(f"Found {len(videos)} pending videos")
                for video in videos:
                    process_video(video)
                    time.sleep(2)  # Be gentle with YouTube
            else:
                logger.debug("No pending videos")

        except Exception as e:
            logger.error(f"Error in main loop: {e}")

        time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    main()
