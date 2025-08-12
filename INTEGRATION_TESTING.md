# Integration Testing Guide

This document explains how to run integration tests for the sttp-openai library against the real OpenAI API.

## Overview

The integration tests are designed to be **cost-efficient** while providing comprehensive coverage of the library's functionality. 

## Running Integration Tests

### Prerequisites

1. **OpenAI API Key**: You need a valid OpenAI API key with sufficient credits
2. **Internet Connection**: Tests make real API calls to OpenAI

### Setup

Set your OpenAI API key as an environment variable:

```bash
export OPENAI_API_KEY=your-api-key-here
```

### Running Tests

#### Run all integration tests:
```bash
sbt "testOnly *OpenAIIntegrationSpec"
```

#### Skip integration tests (when no API key is available):
If `OPENAI_API_KEY` is not set, all tests will be automatically skipped (not failed).
This makes the tests CI/CD friendly.

## Troubleshooting

### "OPENAI_API_KEY not defined - skipping integration test"
This is normal behavior when no API key is set. To run tests:
Set your API key: `export OPENAI_API_KEY=your-key`

### Rate limiting errors
The tests include rate limiting handling, but if you encounter issues:
- Wait a few minutes between test runs
- Ensure your API key has sufficient quota
- Check your OpenAI account usage limits

### Authentication errors
- Verify your API key is correct and active
- Check that your OpenAI account has sufficient credits
- Ensure the key has necessary permissions

### Network timeouts
- Check internet connectivity
- Verify OpenAI API is accessible from your network
- Tests have 30-second timeout configured

## Adding New Integration Tests

When adding new integration tests, follow these guidelines:

1. **Prioritize free/cheap endpoints**
2. **Use minimal inputs** to reduce costs
3. **Set strict limits** on output tokens

Example:
```scala
"New API endpoint" should "work correctly" in {
  withClient { client =>
    //given
    val minimalInput = createMinimalInput()
    
    //when
    val result = client.newEndpoint(minimalInput)
    
    //then
    result should not be null
    validateResult(result)
  }
}

private def createMinimalInput(): RequestType = {
  // Create minimal input to reduce API costs
}

private def validateResult(result: ResponseType): Unit = {
  // Validate structure and content
}
```
