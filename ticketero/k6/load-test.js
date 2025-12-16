import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics
const ticketsCreated = new Counter('tickets_created');
const ticketErrors = new Rate('ticket_errors');
const createLatency = new Trend('create_latency', true);

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const QUEUES = ['CAJA', 'PERSONAL', 'EMPRESAS', 'GERENCIA'];

export const options = {
    vus: 10,
    duration: '2m',
    thresholds: {
        http_req_duration: ['p(95)<2000'],  // p95 < 2s
        ticket_errors: ['rate<0.01'],       // < 1% errors
        tickets_created: ['count>50'],      // > 50 tickets
    },
};

function generateNationalId() {
    return Math.floor(10000000 + Math.random() * 90000000).toString();
}

export default function () {
    const queue = QUEUES[Math.floor(Math.random() * QUEUES.length)];
    
    const payload = JSON.stringify({
        nationalId: generateNationalId(),
        telefono: '+569' + Math.floor(10000000 + Math.random() * 90000000),
        branchOffice: 'Sucursal Test',
        queueType: queue,
    });

    const params = {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'CreateTicket' },
    };

    const startTime = Date.now();
    const response = http.post(`${BASE_URL}/api/tickets`, payload, params);
    const duration = Date.now() - startTime;

    createLatency.add(duration);

    const success = check(response, {
        'status is 201': (r) => r.status === 201,
        'has ticket number': (r) => r.json('numero') !== undefined,
        'has position': (r) => r.json('positionInQueue') > 0,
    });

    if (success) {
        ticketsCreated.add(1);
    } else {
        ticketErrors.add(1);
    }

    sleep(Math.random() * 2 + 1); // 1-3 seconds
}