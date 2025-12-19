#!/usr/bin/env groovy

def call(String xmlFile) {
    def testResults = [
        total: '0',
        passed: '0',
        failed: '0',
        duration: '0.0',
        failedTests: ''
    ]
    
    try {
        def xmlContent = readFile(file: xmlFile)
        def testsuites = new XmlSlurper().parseText(xmlContent)
        
        def totalTests = 0
        def totalFailures = 0
        def totalDuration = 0.0
        def failedTestsList = []
        
        // Handle both <testsuites> wrapper and direct <testsuite>
        def suites = testsuites.name() == 'testsuites' ? testsuites.testsuite : [testsuites]
        
        suites.each { testsuite ->
            // Access attributes as properties
            def tests = testsuite.@tests.toString()
            def failures = testsuite.@failures.toString()
            def time = testsuite.@time.toString()
            
            totalTests += tests ? tests.toInteger() : 0
            totalFailures += failures ? failures.toInteger() : 0
            totalDuration += time ? time.toDouble() : 0.0
            
            // Collect failed test details
            testsuite.testcase.each { testcase ->
                if (testcase.failure.size() > 0) {
                    def testName = testcase.@name.toString()
                    def failureMessage = testcase.failure.text().toString()
                    failedTestsList.add("${testName}: ${failureMessage}")
                }
            }
        }
        
        testResults.total = totalTests.toString()
        testResults.failed = totalFailures.toString()
        testResults.passed = (totalTests - totalFailures).toString()
        testResults.duration = String.format("%.1f", totalDuration)
        testResults.failedTests = failedTestsList.join('\n')
        
    } catch (Exception e) {
        echo "Error parsing JUnit XML: ${e.message}"
        echo "Stack trace: ${e.getStackTrace()}"
        testResults.failedTests = "Error parsing test results: ${e.message}"
    }
    
    return testResults
}
