import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { FieldDescription } from '@/components/ui/field'

/**
 * Onboarding lock (BR-06 / FR-AUTH-02): shown while residency awaits ADS approval.
 * No JWT required — PENDING_APPROVAL accounts cannot log in until activated.
 */
export function PendingApprovalPage() {
  return (
    <main className="flex min-h-svh w-full items-center justify-center p-6 md:p-10">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <p className="text-center text-2xl font-medium tracking-tight">
          <span className="uppercase">Pk</span>ampus
        </p>
        <Card>
          <CardHeader>
            <CardTitle>Waiting for dormitory approval</CardTitle>
            <CardDescription>
              Your email is confirmed. Residency verification is in progress.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <p className="text-sm text-muted-foreground">
              Your account is awaiting residency verification by the dormitory
              administration (ADS). Until then you cannot use reservations or
              report issues.
            </p>
            <p className="text-sm text-muted-foreground">
              You will receive an email when your account is activated. After
              that you can sign in normally.
            </p>
            <Button asChild>
              <Link to="/login">Back to login</Link>
            </Button>
            <FieldDescription className="text-center">
              Wrong account? <Link to="/register">Register again</Link>
            </FieldDescription>
          </CardContent>
        </Card>
      </div>
    </main>
  )
}
