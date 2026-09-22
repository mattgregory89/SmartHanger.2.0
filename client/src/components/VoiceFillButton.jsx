import React, { useState } from 'react';

export default function VoiceFillButton({
  targetId,
  label = 'Listen',
  onTranscript
}) {
  const [listening, setListening] = useState(false);
  const [error, setError] = useState('');

  function startListening() {
    const SpeechRecognition =
      window.SpeechRecognition ||
      window.webkitSpeechRecognition;

    if (!SpeechRecognition) {
      setError('Speech recognition is not supported in this browser.');
      return;
    }

    const recognition = new SpeechRecognition();

    recognition.lang = 'en-US';
    recognition.continuous = false;
    recognition.interimResults = false;

    recognition.onstart = () => {
      setListening(true);
      setError('');
    };

    recognition.onresult = event => {
      const transcript =
        event.results[0][0].transcript;

      if (onTranscript) {
        onTranscript(transcript);
      } else if (targetId) {
        const element =
          document.getElementById(targetId);

        if (element) {
          const existing =
            element.value || '';

          element.value =
            existing
              ? `${existing} ${transcript}`
              : transcript;

          element.dispatchEvent(
            new Event('input', {
              bubbles: true
            })
          );

          element.dispatchEvent(
            new Event('change', {
              bubbles: true
            })
          );
        }
      }
    };

    recognition.onerror = event => {
      console.error(
        'Speech recognition error:',
        event.error
      );

      setError(
        `Voice recognition failed: ${event.error}`
      );

      setListening(false);
    };

    recognition.onend = () => {
      setListening(false);
    };

    recognition.start();
  }

  return (
    <div>
      <button
        type="button"
        className="secondary voice-button"
        onClick={startListening}
        disabled={listening}
      >
        {listening
          ? '🎙 Listening…'
          : `🎙 ${label}`}
      </button>

      {error && (
        <small className="error">
          {error}
        </small>
      )}
    </div>
  );
}