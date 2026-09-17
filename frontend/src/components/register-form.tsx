import { useRef, useState, type ComponentProps, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { cn } from 'cn'

import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Field,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
} from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const MOCK_DORMITORIES = [
  { id: '11111111-1111-1111-1111-111111111111', name: 'DS Akropol' },
  { id: '22222222-2222-2222-2222-222222222222', name: 'DS Olimp' },
  { id: '33333333-3333-3333-3333-333333333333', name: 'DS Neon' },
] as const

const PASSWORD_PATTERN = /^(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z\d]).{8,}$/
const PHONE_PATTERN = /^\+?[0-9]{9,15}$/

type RegisterErrors = Partial<
  Record<
    | 'firstName'
    | 'lastName'
    | 'email'
    | 'password'
    | 'phoneNumber'
    | 'dormitoryId'
    | 'declaredRoomNumber'
    | 'photo',
    string
  >
>

function validateEmail(email: string): string | undefined {
  if (!email.trim()) return 'Email is required'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return 'Invalid email address format'
  if (email.length > 150) return 'Email address cannot exceed 150 characters'
  return undefined
}

export function RegisterForm({
  className,
  ...props
}: ComponentProps<'div'>) {
  const navigate = useNavigate()
  const photoPreviewUrlRef = useRef<string | null>(null)
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [dormitoryId, setDormitoryId] = useState('')
  const [declaredRoomNumber, setDeclaredRoomNumber] = useState('')
  const [photo, setPhoto] = useState<File | null>(null)
  const [photoPreview, setPhotoPreview] = useState<string | null>(null)
  const [errors, setErrors] = useState<RegisterErrors>({})

  function updatePhoto(file: File | null) {
    if (photoPreviewUrlRef.current) {
      URL.revokeObjectURL(photoPreviewUrlRef.current)
      photoPreviewUrlRef.current = null
    }
    setPhoto(file)
    if (!file) {
      setPhotoPreview(null)
      return
    }
    const url = URL.createObjectURL(file)
    photoPreviewUrlRef.current = url
    setPhotoPreview(url)
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const nextErrors: RegisterErrors = {
      firstName: firstName.trim()
        ? firstName.length > 50
          ? 'First name cannot exceed 50 characters'
          : undefined
        : 'First name is required',
      lastName: lastName.trim()
        ? lastName.length > 80
          ? 'Last name cannot exceed 80 characters'
          : undefined
        : 'Last name is required',
      email: validateEmail(email),
      password: password
        ? PASSWORD_PATTERN.test(password)
          ? undefined
          : 'Password must be at least 8 characters and contain an uppercase letter, a digit, and a special character'
        : 'Password is required',
      phoneNumber: phoneNumber.trim()
        ? PHONE_PATTERN.test(phoneNumber)
          ? undefined
          : 'Phone number must contain between 9 and 15 digits (optional + prefix)'
        : 'Phone number is required',
      dormitoryId: dormitoryId ? undefined : 'Dormitory is required',
      declaredRoomNumber: declaredRoomNumber.trim()
        ? declaredRoomNumber.length > 10
          ? 'Room number cannot exceed 10 characters'
          : undefined
        : 'Declared room number is required',
      photo: photo ? undefined : 'Photo is required',
    }

    setErrors(nextErrors)
    if (Object.values(nextErrors).some(Boolean)) return

    navigate('/dashboard')
  }

  return (
    <div className={cn('flex flex-col gap-6', className)} {...props}>
      <p className="text-center text-2xl font-medium tracking-tight">
        <span className="uppercase">Pk</span>ampus
      </p>
      <Card>
        <CardHeader>
          <CardTitle>Create an account</CardTitle>
          <CardDescription>
            Enter your details below to register as a resident
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} noValidate>
            <FieldGroup>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field data-invalid={Boolean(errors.firstName) || undefined}>
                  <FieldLabel htmlFor="firstName">First name</FieldLabel>
                  <Input
                    id="firstName"
                    autoComplete="given-name"
                    value={firstName}
                    onChange={(e) => setFirstName(e.target.value)}
                    aria-invalid={Boolean(errors.firstName)}
                  />
                  {errors.firstName ? <FieldError>{errors.firstName}</FieldError> : null}
                </Field>
                <Field data-invalid={Boolean(errors.lastName) || undefined}>
                  <FieldLabel htmlFor="lastName">Last name</FieldLabel>
                  <Input
                    id="lastName"
                    autoComplete="family-name"
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    aria-invalid={Boolean(errors.lastName)}
                  />
                  {errors.lastName ? <FieldError>{errors.lastName}</FieldError> : null}
                </Field>
              </div>

              <Field data-invalid={Boolean(errors.email) || undefined}>
                <FieldLabel htmlFor="register-email">Email</FieldLabel>
                <Input
                  id="register-email"
                  type="email"
                  placeholder="m@example.com"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  aria-invalid={Boolean(errors.email)}
                />
                {errors.email ? <FieldError>{errors.email}</FieldError> : null}
              </Field>

              <Field data-invalid={Boolean(errors.password) || undefined}>
                <FieldLabel htmlFor="register-password">Password</FieldLabel>
                <Input
                  id="register-password"
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  aria-invalid={Boolean(errors.password)}
                />
                {errors.password ? <FieldError>{errors.password}</FieldError> : null}
              </Field>

              <Field data-invalid={Boolean(errors.phoneNumber) || undefined}>
                <FieldLabel htmlFor="phoneNumber">Phone number</FieldLabel>
                <Input
                  id="phoneNumber"
                  type="tel"
                  placeholder="+48123456789"
                  autoComplete="tel"
                  value={phoneNumber}
                  onChange={(e) => setPhoneNumber(e.target.value)}
                  aria-invalid={Boolean(errors.phoneNumber)}
                />
                {errors.phoneNumber ? <FieldError>{errors.phoneNumber}</FieldError> : null}
              </Field>

              <Field data-invalid={Boolean(errors.dormitoryId) || undefined}>
                <FieldLabel htmlFor="dormitoryId">Dormitory</FieldLabel>
                <Select value={dormitoryId} onValueChange={setDormitoryId}>
                  <SelectTrigger id="dormitoryId" className="w-full" aria-invalid={Boolean(errors.dormitoryId)}>
                    <SelectValue placeholder="Select a dormitory" />
                  </SelectTrigger>
                  <SelectContent>
                    {MOCK_DORMITORIES.map((dorm) => (
                      <SelectItem key={dorm.id} value={dorm.id}>
                        {dorm.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.dormitoryId ? <FieldError>{errors.dormitoryId}</FieldError> : null}
              </Field>

              <Field data-invalid={Boolean(errors.declaredRoomNumber) || undefined}>
                <FieldLabel htmlFor="declaredRoomNumber">Declared room number</FieldLabel>
                <Input
                  id="declaredRoomNumber"
                  value={declaredRoomNumber}
                  onChange={(e) => setDeclaredRoomNumber(e.target.value)}
                  aria-invalid={Boolean(errors.declaredRoomNumber)}
                />
                {errors.declaredRoomNumber ? (
                  <FieldError>{errors.declaredRoomNumber}</FieldError>
                ) : null}
              </Field>

              <Field data-invalid={Boolean(errors.photo) || undefined}>
                <FieldLabel htmlFor="photo">Photo</FieldLabel>
                <Input
                  id="photo"
                  type="file"
                  accept="image/*"
                  onChange={(e) => updatePhoto(e.target.files?.[0] ?? null)}
                  aria-invalid={Boolean(errors.photo)}
                />
                {errors.photo ? <FieldError>{errors.photo}</FieldError> : null}
                {photoPreview ? (
                  <img
                    src={photoPreview}
                    alt="Selected registration photo preview"
                    className="mt-1 h-24 w-24 rounded-md border object-cover"
                  />
                ) : null}
              </Field>

              <Field>
                <Button type="submit">Create account</Button>
                <FieldDescription className="text-center">
                  Already have an account? <Link to="/login">Login</Link>
                </FieldDescription>
              </Field>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
