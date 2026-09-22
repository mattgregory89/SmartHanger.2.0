import React from 'react';

export default function SmartHangarLogo({
  size = 44,
  className = ''
}) {
  return (
    <img
      src="/smarthangar-logo.png"
      alt="SmartHangar"
      className={`smarthangar-logo ${className}`}
      style={{
        width: size,
        height: size,
        objectFit: 'contain'
      }}
    />
  );
}