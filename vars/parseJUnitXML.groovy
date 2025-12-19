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
        def parser = new XmlParser()
        def testsuites = parser.parseText(xmlContent)
        
        def totalTests = 0
        def totalFailures = 0
        def totalDuration = 0.0
        def failedTestsList = []
        
        // Handle both <testsuites> wrapper and direct <testsuite>
        def suites = testsuites.name() == 'testsuites' ? testsuites.testsuite : [testsuites]
        
        suites.each { testsuite ->
            // XmlParser uses @attribute syntax directly
            totalTests += (testsuite.@tests ?: '0') as Integer
            totalFailures += (testsuite.@failures ?: '0') as Integer
            totalDuration += (testsuite.@time ?: '0') as Double
            
            // Collect failed test details
            testsuite.testcase.each { testcase ->
                def failures = testcase.failure
                if (failures && !failures.isEmpty()) {
                    def testName = testcase.@name
                    def failureMessage = failures[0].text()
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
        echo "Exception class: ${e.class.name}"
        e.printStackTrace()
        testResults.failedTests = "Error parsing test results: ${e.message}"
    }
    
    return testResults
}
